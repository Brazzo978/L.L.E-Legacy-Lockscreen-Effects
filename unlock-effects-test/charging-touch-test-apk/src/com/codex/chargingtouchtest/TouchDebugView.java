package com.codex.chargingtouchtest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class TouchDebugView extends View {
    private static final String TAG = "ChargingTouchDebug";

    interface TouchTriggerListener {
        void onTouchStarted(float rawX, float rawY);

        void onTouchMoved(float rawX, float rawY, float deltaX, float deltaY, float distance);

        void onTouchEnded(float rawX, float rawY, float deltaX, float deltaY, float distance);

        void onTouchCancelled();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private ToneGenerator toneGenerator;
    private SoundPool soundPool;
    private int tapSoundId;
    private boolean tapSoundLoaded;
    private TouchTriggerListener touchTriggerListener;
    private String lastAction = "waiting";
    private float lastX = -1f;
    private float lastY = -1f;
    private float lastRawX = -1f;
    private float lastRawY = -1f;
    private float gestureStartRawX = -1f;
    private float gestureStartRawY = -1f;
    private int pointerCount;
    private boolean transparentMode = true;
    private boolean gestureActive;

    public TouchDebugView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        toneGenerator = createToneGenerator();
        soundPool = createSoundPool();
        if (soundPool != null) {
            soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                    tapSoundLoaded = status == 0 && sampleId == tapSoundId;
                }
            });
            tapSoundId = soundPool.load(context, R.raw.lens_flare_tap, 1);
        }
    }

    public void setTouchTriggerListener(TouchTriggerListener touchTriggerListener) {
        this.touchTriggerListener = touchTriggerListener;
    }

    public void setTransparentMode(boolean transparentMode) {
        if (this.transparentMode == transparentMode) {
            return;
        }
        this.transparentMode = transparentMode;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return true;
        }
        lastAction = actionName(event.getActionMasked());
        lastX = event.getX();
        lastY = event.getY();
        lastRawX = event.getRawX();
        lastRawY = event.getRawY();
        pointerCount = event.getPointerCount();
        Log.i(TAG, "action=" + lastAction
                + " local=" + Math.round(lastX) + "," + Math.round(lastY)
                + " raw=" + Math.round(lastRawX) + "," + Math.round(lastRawY)
                + " pointers=" + pointerCount);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureActive = true;
                gestureStartRawX = lastRawX;
                gestureStartRawY = lastRawY;
                playClickTone();
                if (touchTriggerListener != null) {
                    touchTriggerListener.onTouchStarted(lastRawX, lastRawY);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                notifyMove();
                break;
            case MotionEvent.ACTION_UP:
                notifyEnd();
                performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                gestureActive = false;
                if (touchTriggerListener != null) {
                    touchTriggerListener.onTouchCancelled();
                }
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (transparentMode) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        bounds.set(0f, 0f, width, height);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 8, 18, 28));
        canvas.drawRoundRect(bounds, dp(12), dp(12), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(230, 116, 215, 255));
        canvas.drawRoundRect(bounds, dp(12), dp(12), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        paint.setTextSize(dp(15));
        paint.setColor(Color.WHITE);
        float x = dp(12);
        float y = dp(24);
        canvas.drawText("TOUCH DEBUG AREA", x, y, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(dp(13));
        paint.setColor(Color.rgb(205, 224, 236));
        y += dp(24);
        canvas.drawText("swipe: lens flare", x, y, paint);
        y += dp(20);
        canvas.drawText("action: " + lastAction, x, y, paint);
        y += dp(20);
        canvas.drawText("local: " + point(lastX, lastY), x, y, paint);
        y += dp(20);
        canvas.drawText("raw: " + point(lastRawX, lastRawY), x, y, paint);
        y += dp(20);
        canvas.drawText("pointers: " + pointerCount, x, y, paint);

        if (lastX >= 0f && lastY >= 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(210, 255, 210, 82));
            canvas.drawCircle(lastX, lastY, dp(8), paint);
        }
    }

    private void notifyMove() {
        if (!gestureActive || touchTriggerListener == null) {
            return;
        }
        float deltaX = lastRawX - gestureStartRawX;
        float deltaY = lastRawY - gestureStartRawY;
        float distance = (float) Math.hypot(deltaX, deltaY);
        touchTriggerListener.onTouchMoved(lastRawX, lastRawY, deltaX, deltaY, distance);
    }

    private void notifyEnd() {
        if (!gestureActive) {
            return;
        }
        gestureActive = false;
        if (touchTriggerListener == null) {
            return;
        }
        float deltaX = lastRawX - gestureStartRawX;
        float deltaY = lastRawY - gestureStartRawY;
        float distance = (float) Math.hypot(deltaX, deltaY);
        touchTriggerListener.onTouchEnded(lastRawX, lastRawY, deltaX, deltaY, distance);
    }

    private String point(float x, float y) {
        if (x < 0f || y < 0f) {
            return "-";
        }
        return Math.round(x) + "," + Math.round(y);
    }

    private String actionName(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            case MotionEvent.ACTION_POINTER_DOWN:
                return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP:
                return "POINTER_UP";
            default:
                return String.valueOf(action);
        }
    }

    private ToneGenerator createToneGenerator() {
        try {
            return new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (RuntimeException e) {
            Log.w(TAG, "tone generator unavailable", e);
            return null;
        }
    }

    private SoundPool createSoundPool() {
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            return new SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(audioAttributes)
                    .build();
        } catch (RuntimeException e) {
            Log.w(TAG, "sound pool unavailable", e);
            return null;
        }
    }

    private void playClickTone() {
        if (soundPool != null && tapSoundLoaded) {
            soundPool.play(tapSoundId, 1f, 1f, 1, 0, 1f);
            Log.i(TAG, "lens flare tap sound played");
            return;
        }
        if (toneGenerator == null) {
            return;
        }
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        Log.i(TAG, "click tone played");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
