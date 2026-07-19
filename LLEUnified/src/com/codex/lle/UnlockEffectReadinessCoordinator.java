package com.codex.lle;

import android.os.SystemClock;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/**
 * Main-thread readiness state shared by the non-direct-GL unlock renderers.
 *
 * <p>Samsung's {@code EffectView} constructors create their Java renderer immediately, while
 * their TextureView, SurfaceTexture and EGL resources only become usable after the hierarchy is
 * attached and laid out. This coordinator deliberately observes that public View lifecycle
 * without replacing Samsung's private SurfaceTextureListener.</p>
 */
final class UnlockEffectReadinessCoordinator {
    private static final long VENDOR_READY_TIMEOUT_MS = 1_500L;
    private static final long VENDOR_READY_POLL_MS = 16L;
    private static final int REQUIRED_STABLE_POLLS = 2;

    private final ViewGroup host;
    private final String rendererName;

    private volatile int state = UnlockEffectReadiness.STATE_CONSTRUCTED;
    private volatile String detail = "constructed";
    private volatile UnlockEffectReadiness.ReadinessListener listener;

    private int generation;
    private boolean terminalFailure;
    private long readyDeadlineMs;
    private int stableReadyPolls;
    private View vendorContent;
    private Runnable vendorWarmUp;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;

    private final Runnable vendorReadyPoll = new Runnable() {
        @Override
        public void run() {
            pollVendorReady(generation);
        }
    };

    private final Runnable vendorReadyTimeout = new Runnable() {
        @Override
        public void run() {
            if (!terminalFailure
                    && state != UnlockEffectReadiness.STATE_FIRST_FRAME_READY
                    && state != UnlockEffectReadiness.STATE_DETACHED) {
                failRecoverably("vendor readiness timed out: " + hierarchyDetail());
            }
        }
    };

    UnlockEffectReadinessCoordinator(ViewGroup host, String rendererName) {
        this.host = host;
        this.rendererName = rendererName;
    }

    int getState() {
        return state;
    }

    String getDetail() {
        return detail;
    }

    void setListener(UnlockEffectReadiness.ReadinessListener listener) {
        this.listener = listener;
        if (listener != null) {
            host.post(new Runnable() {
                @Override
                public void run() {
                    notifyListener();
                }
            });
        }
    }

    void constructionFailed(String reason) {
        terminalFailure = true;
        cancelPendingChecks();
        transition(UnlockEffectReadiness.STATE_FAILED, "construction failed: " + reason);
    }

    void rendererUnavailable(String reason) {
        terminalFailure = true;
        cancelPendingChecks();
        transition(UnlockEffectReadiness.STATE_FAILED, "renderer unavailable: " + reason);
    }

    void attachVendor(View content, Runnable warmUp) {
        if (terminalFailure) {
            return;
        }
        cancelPendingChecks();
        generation++;
        vendorContent = content;
        vendorWarmUp = warmUp;
        stableReadyPolls = 0;
        readyDeadlineMs = SystemClock.uptimeMillis() + VENDOR_READY_TIMEOUT_MS;
        transition(UnlockEffectReadiness.STATE_ATTACHED, "attached; waiting for vendor surface");
        host.post(vendorReadyPoll);
        host.postDelayed(vendorReadyTimeout, VENDOR_READY_TIMEOUT_MS);
    }

    void attachCanvas() {
        if (terminalFailure) {
            return;
        }
        cancelPendingChecks();
        generation++;
        transition(UnlockEffectReadiness.STATE_ATTACHED, "canvas attached; waiting for warm draw");
    }

    void canvasWarmFrameDrawn() {
        if (terminalFailure || !host.isAttachedToWindow()) {
            return;
        }
        transition(UnlockEffectReadiness.STATE_SURFACE_READY, "HWUI canvas available");
        transition(UnlockEffectReadiness.STATE_RESOURCES_READY, "canvas assets prepared");
        transition(UnlockEffectReadiness.STATE_FIRST_FRAME_READY, "warm-up frame drawn");
    }

    void detached(String reason) {
        if (terminalFailure) {
            return;
        }
        cancelPendingChecks();
        generation++;
        vendorContent = null;
        vendorWarmUp = null;
        transition(UnlockEffectReadiness.STATE_DETACHED, reason);
    }

    void destroyed() {
        terminalFailure = true;
        cancelPendingChecks();
        generation++;
        vendorContent = null;
        vendorWarmUp = null;
        transition(UnlockEffectReadiness.STATE_FAILED, "renderer destroyed");
        listener = null;
    }

    private void pollVendorReady(int expectedGeneration) {
        if (expectedGeneration != generation || terminalFailure
                || state == UnlockEffectReadiness.STATE_DETACHED
                || state == UnlockEffectReadiness.STATE_FAILED) {
            return;
        }
        if (SystemClock.uptimeMillis() >= readyDeadlineMs) {
            failRecoverably("vendor readiness timed out: " + hierarchyDetail());
            return;
        }
        if (!isVendorHierarchyReady()) {
            stableReadyPolls = 0;
            host.postDelayed(vendorReadyPoll, VENDOR_READY_POLL_MS);
            return;
        }
        if (++stableReadyPolls < REQUIRED_STABLE_POLLS) {
            host.postDelayed(vendorReadyPoll, VENDOR_READY_POLL_MS);
            return;
        }

        transition(UnlockEffectReadiness.STATE_SURFACE_READY, hierarchyDetail());
        Runnable warmUp = vendorWarmUp;
        if (warmUp != null) {
            warmUp.run();
        }
        transition(UnlockEffectReadiness.STATE_RESOURCES_READY,
                "vendor warm-up submitted; waiting for pre-draw");
        awaitVendorPreDraw(expectedGeneration);
    }

    private void awaitVendorPreDraw(final int expectedGeneration) {
        removePreDrawListener();
        preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (expectedGeneration != generation || terminalFailure) {
                    removePreDrawListener();
                    return true;
                }
                if (!isVendorHierarchyReady()) {
                    removePreDrawListener();
                    stableReadyPolls = 0;
                    host.post(vendorReadyPoll);
                    return true;
                }
                removePreDrawListener();
                host.postOnAnimation(new Runnable() {
                    @Override
                    public void run() {
                        if (expectedGeneration == generation
                                && !terminalFailure
                                && isVendorHierarchyReady()) {
                            host.removeCallbacks(vendorReadyTimeout);
                            transition(UnlockEffectReadiness.STATE_FIRST_FRAME_READY,
                                    "vendor surface warmed after pre-draw");
                        } else if (expectedGeneration == generation && !terminalFailure) {
                            stableReadyPolls = 0;
                            host.post(vendorReadyPoll);
                        }
                    }
                });
                return true;
            }
        };
        ViewTreeObserver observer = host.getViewTreeObserver();
        if (!observer.isAlive()) {
            failRecoverably("ViewTreeObserver unavailable");
            return;
        }
        observer.addOnPreDrawListener(preDrawListener);
        host.invalidate();
        if (vendorContent != null) {
            vendorContent.invalidate();
        }
        host.postInvalidateOnAnimation();
    }

    private boolean isVendorHierarchyReady() {
        if (!host.isAttachedToWindow() || !host.isLaidOut()
                || host.getWidth() <= 0 || host.getHeight() <= 0
                || vendorContent == null || !vendorContent.isAttachedToWindow()
                || !vendorContent.isLaidOut()) {
            return false;
        }
        TextureStatus status = new TextureStatus();
        collectTextureStatus(vendorContent, status);
        return status.unavailable == 0;
    }

    private String hierarchyDetail() {
        TextureStatus status = new TextureStatus();
        collectTextureStatus(vendorContent, status);
        return "view=" + (vendorContent != null)
                + " attached=" + host.isAttachedToWindow()
                + " laidOut=" + host.isLaidOut()
                + " size=" + host.getWidth() + "x" + host.getHeight()
                + " textures=" + status.total
                + " unavailable=" + status.unavailable;
    }

    private void collectTextureStatus(View view, TextureStatus status) {
        if (view == null) {
            return;
        }
        if (view instanceof TextureView) {
            status.total++;
            TextureView textureView = (TextureView) view;
            if (!textureView.isAttachedToWindow() || !textureView.isAvailable()) {
                status.unavailable++;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectTextureStatus(group.getChildAt(index), status);
            }
        }
    }

    private void failRecoverably(String reason) {
        cancelPendingChecks();
        transition(UnlockEffectReadiness.STATE_FAILED, reason);
    }

    private void cancelPendingChecks() {
        host.removeCallbacks(vendorReadyPoll);
        host.removeCallbacks(vendorReadyTimeout);
        removePreDrawListener();
    }

    private void removePreDrawListener() {
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        preDrawListener = null;
        if (listener == null) {
            return;
        }
        ViewTreeObserver observer = host.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(listener);
        }
    }

    private void transition(int nextState, String nextDetail) {
        String qualifiedDetail = rendererName + ": " + nextDetail;
        if (state == nextState && qualifiedDetail.equals(detail)) {
            return;
        }
        state = nextState;
        detail = qualifiedDetail;
        notifyListener();
    }

    private void notifyListener() {
        UnlockEffectReadiness.ReadinessListener current = listener;
        if (current != null) {
            try {
                current.onReadinessChanged();
            } catch (RuntimeException ignored) {
                // Readiness is advisory and must never break a renderer lifecycle callback.
            }
        }
    }

    private static final class TextureStatus {
        int total;
        int unavailable;
    }
}
