package com.codex.lle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;

public class ControlActivity extends Activity {
    private static final String STATE_SELECTED_TAB = "selected_tab";
    private static final int TAB_LOCKSCREEN_EFFECT = 0;
    private static final int TAB_CHARGING_DOODLE = 1;
    private static final int COLOR_BACKGROUND = Color.rgb(238, 246, 251);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(33, 33, 33);
    private static final int COLOR_MUTED = Color.rgb(117, 117, 117);
    private static final int COLOR_DIVIDER = Color.rgb(230, 230, 230);
    private static final int COLOR_ACCENT = Color.rgb(64, 152, 160);
    private static final int COLOR_ACCENT_DEEP = Color.rgb(0, 132, 142);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(231, 247, 248);
    private static final int COLOR_OK = Color.rgb(22, 160, 98);
    private static final int COLOR_ERROR = Color.rgb(207, 67, 72);
    private static final int COLOR_HEADER_TOP = Color.rgb(244, 249, 252);
    private static final int COLOR_HEADER_BOTTOM = Color.WHITE;
    private static final int COLOR_GRACE_NAVY = Color.rgb(39, 55, 111);
    private static final int COLOR_GRACE_BLUE = Color.rgb(61, 111, 169);
    private static final int COLOR_GRACE_AQUA = Color.rgb(91, 199, 194);
    private static final int COLOR_GRACE_LILAC = Color.rgb(106, 79, 176);
    private static final int TAB_SWIPE_MIN_DISTANCE_DP = 72;
    private static final int TAB_DRAG_START_DISTANCE_DP = 12;
    private static final long TAB_ANIMATION_DURATION_MS = 270L;
    private static final float TAB_SWIPE_AXIS_RATIO = 1.35f;
    private static final float TAB_DRAG_AXIS_RATIO = 1.18f;
    private static final long EFFECT_SELECTION_APPLY_DELAY_MS = 2000L;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable applyPendingUnlockEffectRunnable = new Runnable() {
        @Override
        public void run() {
            applyPendingUnlockEffect();
        }
    };
    private SharedPreferences prefs;
    private TextView accessibilityStatus;
    private Switch serviceSwitch;
    private Button chargingDoodleTabButton;
    private Button lockscreenEffectTabButton;
    private FrameLayout tabPager;
    private LinearLayout tabContent;
    private LinearLayout tabAdjacentContent;
    private TextView touchBoxSummary;
    private TextView effectProfilerSummary;
    private int selectedTab = TAB_LOCKSCREEN_EFFECT;
    private int pendingUnlockEffect = -1;
    private int pendingAbstractTilesLineMode = -1;
    private boolean updatingServiceSwitch;
    private float tabSwipeDownX;
    private float tabSwipeDownY;
    private boolean tabSwipeStartedOnSlider;
    private boolean tabSwipeDragging;
    private int tabSwipeTarget = -1;
    private int tabSwipeDirection;
    private int tabAdjacentTab = -1;
    private int tabAdjacentDirection;
    private boolean tabAnimationRunning;
    private boolean doodleDebugExpanded;
    private boolean doodlePositionExpanded;
    private boolean lockscreenDebugExpanded;
    private final HashSet<String> expandedTimingSections = new HashSet<String>();
    private Typeface appFontRegular;
    private Typeface appFontBold;
    private PopupWindow effectPreviewPopup;
    private Bitmap effectPreviewPopupBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("LLE64", "native baseline abi=" + Lle64Abi.verify());
        configureGraceWindow();
        prefs = OverlayPrefs.get(this);
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this);
        ensureTouchAreaEnabled();
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_SELECTED_TAB, TAB_LOCKSCREEN_EFFECT);
        }

        FrameLayout scene = new FrameLayout(this);
        scene.addView(new GraceBackdropView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.TRANSPARENT);
        outer.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        final View headerView = appHeader();
        outer.addView(headerView, headerParams);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(0, dp(8), 0, dp(24));
        root.setBackgroundColor(Color.TRANSPARENT);

        final View tabsView = tabSelector();
        root.addView(tabsView);

        tabPager = new FrameLayout(this);
        tabPager.setClipChildren(true);
        tabPager.setClipToPadding(true);
        root.addView(tabPager, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tabContent = createTabPage();
        tabPager.addView(tabContent);
        showTab(selectedTab, false, 0);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        outer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        scene.addView(outer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(scene);
        forceSansSerif(outer);
        updateAccessibilityStatus();
        playGraceEntrance(headerView, tabsView, tabPager);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SELECTED_TAB, selectedTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
        updateTouchBoxSummary();
        updateEffectProfilerSummary();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(applyPendingUnlockEffectRunnable);
        hideEffectPreviewBubble();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (trackTabSwipe(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void configureGraceWindow() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.rgb(232, 241, 246));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void playGraceEntrance(final View header, final View tabs, final View content) {
        if (header == null || tabs == null || content == null) {
            return;
        }
        header.setAlpha(0f);
        header.setTranslationY(-dp(18));
        tabs.setAlpha(0f);
        tabs.setTranslationY(dp(14));
        content.setAlpha(0f);
        content.setTranslationY(dp(28));
        content.setScaleX(0.985f);
        content.setScaleY(0.985f);
        header.post(new Runnable() {
            @Override
            public void run() {
                header.animate().alpha(1f).translationY(0f)
                        .setDuration(420L).setInterpolator(tabEnterInterpolator()).start();
                tabs.animate().alpha(1f).translationY(0f)
                        .setStartDelay(80L).setDuration(420L)
                        .setInterpolator(tabEnterInterpolator()).start();
                content.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                        .setStartDelay(150L).setDuration(520L)
                        .setInterpolator(tabEnterInterpolator()).start();
            }
        });
    }

    private View appHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), statusBarHeight() + dp(15), dp(16), dp(16));
        header.setBackground(headerBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            header.setElevation(dp(7));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(titleStack, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView title = new TextView(this);
        title.setText("L.L.E");
        title.setTextColor(COLOR_GRACE_NAVY);
        title.setTextSize(29f);
        title.setSingleLine(true);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            title.setLetterSpacing(0.10f);
        }
        titleStack.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Legacy Lockscreen effect");
        subtitle.setTextColor(Color.rgb(65, 75, 104));
        subtitle.setTextSize(11f);
        subtitle.setSingleLine(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            subtitle.setLetterSpacing(0.06f);
        }
        titleStack.addView(subtitle);

        serviceSwitch = new Switch(this);
        serviceSwitch.setText("");
        serviceSwitch.setTextSize(13f);
        serviceSwitch.setTextColor(COLOR_TEXT);
        serviceSwitch.setMinWidth(dp(52));
        serviceSwitch.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            serviceSwitch.setShowText(false);
        }
        styleHeaderSwitch(serviceSwitch);
        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (updatingServiceSwitch) {
                    return;
                }
                boolean accessibilityEnabled = isChargingAccessibilityEnabled();
                if (isChecked && !accessibilityEnabled) {
                    Toast.makeText(ControlActivity.this,
                            "Enable accessibility first", Toast.LENGTH_SHORT).show();
                    openAccessibilitySettings();
                    updateAccessibilityStatus();
                    return;
                }
                prefs.edit().putBoolean(OverlayPrefs.MASTER_ENABLED, isChecked).apply();
                updateAccessibilityStatus();
            }
        });
        row.addView(serviceSwitch, new LinearLayout.LayoutParams(
                dp(58),
                dp(44)));

        accessibilityStatus = new TextView(this);
        accessibilityStatus.setGravity(Gravity.CENTER);
        accessibilityStatus.setTextSize(18f);
        accessibilityStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        accessibilityStatus.setBackground(statusBadgeBackground(
                Color.argb(120, 72, 89, 115), Color.argb(45, 255, 255, 255)));
        accessibilityStatus.setClickable(true);
        accessibilityStatus.setFocusable(true);
        accessibilityStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAccessibilitySettings();
            }
        });
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                dp(36),
                dp(36));
        statusParams.setMargins(dp(7), 0, 0, 0);
        row.addView(accessibilityStatus, statusParams);
        return header;
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private View tabSelector() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(5), dp(5), dp(5), dp(5));
        tabs.setBackground(solidDrawable(Color.argb(242, 255, 255, 255), dp(22),
                Color.argb(90, 177, 198, 210), dp(1)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tabs.setElevation(dp(4));
        }
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60));
        tabsParams.setMargins(dp(14), dp(12), dp(14), dp(10));
        tabs.setLayoutParams(tabsParams);

        lockscreenEffectTabButton = tabButton("LOCKSCREEN", TAB_LOCKSCREEN_EFFECT);
        chargingDoodleTabButton = tabButton("CHARGING", TAB_CHARGING_DOODLE);

        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f);
        tabs.addView(lockscreenEffectTabButton, firstParams);

        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f);
        secondParams.setMargins(dp(4), 0, 0, 0);
        tabs.addView(chargingDoodleTabButton, secondParams);
        return tabs;
    }

    private Button tabButton(String label, final int tab) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTab(tab);
            }
        });
        return button;
    }

    private void showTab(int tab) {
        int targetTab = normalizeTab(tab);
        int direction = targetTab >= selectedTab ? 1 : -1;
        showTab(targetTab, true, direction);
    }

    private void showTab(final int tab, boolean animate, final int direction) {
        final int targetTab = normalizeTab(tab);
        if (tabContent == null) {
            selectedTab = targetTab;
            updateTabStyles();
            return;
        }
        boolean sameTab = targetTab == selectedTab;
        boolean canAnimate = animate
                && !sameTab
                && !tabAnimationRunning
                && tabContent.getChildCount() > 0
                && tabPagerWidth() > 0f;

        selectedTab = targetTab;
        updateTabStyles();

        if (!canAnimate) {
            tabContent.animate().cancel();
            populateTabContent(targetTab);
            tabContent.setAlpha(1f);
            tabContent.setTranslationX(0f);
            tabContent.setTranslationY(0f);
            tabContent.setScaleX(1f);
            tabContent.setScaleY(1f);
            return;
        }
        animateTabContentChange(targetTab, direction);
    }

    private int normalizeTab(int tab) {
        return tab == TAB_CHARGING_DOODLE ? TAB_CHARGING_DOODLE : TAB_LOCKSCREEN_EFFECT;
    }

    private LinearLayout createTabPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(2), dp(12), dp(28));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        return page;
    }

    private void populateTabContent(int tab) {
        hideEffectPreviewBubble();
        clearAdjacentTabContent();
        if (tabContent == null) {
            tabContent = createTabPage();
        }
        if (tabPager != null && tabContent.getParent() != tabPager) {
            tabPager.addView(tabContent);
        }
        populateTabPage(tabContent, tab);
        resetTabPageTransform(tabContent);
    }

    private void populateTabPage(LinearLayout page, int tab) {
        if (page == null) {
            return;
        }
        page.removeAllViews();
        if (tab == TAB_CHARGING_DOODLE) {
            page.addView(chargingDoodleControls());
        } else {
            page.addView(effectSelector());
            page.addView(lockscreenTouchControls());
            page.addView(lockscreenDebugMenu());
        }
        forceSansSerif(page);
    }

    private float tabPagerWidth() {
        if (tabPager != null && tabPager.getWidth() > 0) {
            return tabPager.getWidth();
        }
        if (tabContent != null && tabContent.getWidth() > 0) {
            return tabContent.getWidth();
        }
        return Math.max(dp(120), getResources().getDisplayMetrics().widthPixels);
    }

    private void resetTabPageTransform(View page) {
        if (page == null) {
            return;
        }
        page.animate().setListener(null).cancel();
        page.setAlpha(1f);
        page.setTranslationX(0f);
        page.setTranslationY(0f);
        page.setScaleX(1f);
        page.setScaleY(1f);
    }

    private LinearLayout prepareAdjacentTabContent(int targetTab, int direction) {
        int target = normalizeTab(targetTab);
        int tabDirection = direction == 0 ? 1 : direction;
        float width = tabPagerWidth();
        if (tabAdjacentContent != null
                && tabAdjacentTab == target
                && tabAdjacentDirection == tabDirection) {
            tabAdjacentContent.setTranslationX(tabDirection * width);
            tabAdjacentContent.setAlpha(1f);
            return tabAdjacentContent;
        }
        clearAdjacentTabContent();
        tabAdjacentContent = createTabPage();
        tabAdjacentTab = target;
        tabAdjacentDirection = tabDirection;
        populateTabPage(tabAdjacentContent, target);
        tabAdjacentContent.setTranslationX(tabDirection * width);
        tabAdjacentContent.setAlpha(1f);
        if (tabPager != null) {
            tabPager.addView(tabAdjacentContent);
        }
        return tabAdjacentContent;
    }

    private void clearAdjacentTabContent() {
        if (tabAdjacentContent != null) {
            tabAdjacentContent.animate().setListener(null).cancel();
            if (tabPager != null) {
                tabPager.removeView(tabAdjacentContent);
            }
        }
        tabAdjacentContent = null;
        tabAdjacentTab = -1;
        tabAdjacentDirection = 0;
    }

    private void promoteAdjacentTabContent() {
        if (tabAdjacentContent == null) {
            populateTabContent(selectedTab);
            return;
        }
        LinearLayout oldContent = tabContent;
        tabContent = tabAdjacentContent;
        tabAdjacentContent = null;
        tabAdjacentTab = -1;
        tabAdjacentDirection = 0;
        resetTabPageTransform(tabContent);
        if (oldContent != null && tabPager != null) {
            oldContent.animate().setListener(null).cancel();
            tabPager.removeView(oldContent);
        }
        if (tabPager != null && tabContent.getParent() != tabPager) {
            tabPager.addView(tabContent);
        }
    }

    private void animateTabContentChange(final int targetTab, final int direction) {
        animatePagerTabSwitch(targetTab, direction);
    }

    private void animatePagerTabSwitch(final int targetTab, final int direction) {
        if (tabContent == null) {
            selectedTab = normalizeTab(targetTab);
            updateTabStyles();
            return;
        }
        final int target = normalizeTab(targetTab);
        final int tabDirection = direction == 0 ? 1 : direction;
        final float width = tabPagerWidth();
        final LinearLayout adjacent = prepareAdjacentTabContent(target, tabDirection);
        if (adjacent == null) {
            populateTabContent(target);
            finishTabAnimation();
            return;
        }
        tabAnimationRunning = true;
        tabContent.animate().setListener(null).cancel();
        adjacent.animate().setListener(null).cancel();
        tabContent.animate()
                .translationX(-tabDirection * width)
                .alpha(1f)
                .setDuration(TAB_ANIMATION_DURATION_MS)
                .setInterpolator(tabEnterInterpolator())
                .start();
        adjacent.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(TAB_ANIMATION_DURATION_MS)
                .setInterpolator(tabEnterInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        promoteAdjacentTabContent();
                        finishTabAnimation();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        finishTabAnimation();
                    }
                })
                .start();
    }

    private void finishTabAnimation() {
        if (tabContent != null) {
            resetTabPageTransform(tabContent);
        }
        tabAnimationRunning = false;
    }

    private Interpolator tabExitInterpolator() {
        return new PathInterpolator(0.40f, 0.00f, 1.00f, 1.00f);
    }

    private Interpolator tabEnterInterpolator() {
        return new PathInterpolator(0.00f, 0.00f, 0.20f, 1.00f);
    }

    private boolean trackTabSwipe(MotionEvent event) {
        if (event == null) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            tabSwipeDownX = event.getRawX();
            tabSwipeDownY = event.getRawY();
            tabSwipeDragging = false;
            tabSwipeStartedOnSlider = isPointInsideViewType(
                    getWindow().getDecorView(),
                    tabSwipeDownX,
                    tabSwipeDownY,
                    SeekBar.class);
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            return handleTabSwipeMove(event);
        }
        if (action == MotionEvent.ACTION_UP) {
            if (tabSwipeDragging) {
                finishInteractiveTabSwipe(event, false);
                return true;
            }
            boolean handled = maybeSwitchTabBySwipe(event);
            resetTabSwipeState();
            return handled;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            if (tabSwipeDragging) {
                finishInteractiveTabSwipe(event, true);
                return true;
            }
            resetTabSwipeState();
        }
        return false;
    }

    private boolean handleTabSwipeMove(MotionEvent event) {
        if (tabSwipeStartedOnSlider || tabAnimationRunning || tabContent == null) {
            return false;
        }
        float dx = event.getRawX() - tabSwipeDownX;
        float dy = event.getRawY() - tabSwipeDownY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        if (!tabSwipeDragging) {
            int target = swipeTargetForDx(dx);
            if (absX < dp(TAB_DRAG_START_DISTANCE_DP)
                    || absX < absY * TAB_DRAG_AXIS_RATIO
                    || target < 0) {
                return false;
            }
            hideEffectPreviewBubble();
            tabSwipeDragging = true;
            tabSwipeTarget = target;
            tabSwipeDirection = target >= selectedTab ? 1 : -1;
            tabContent.animate().setListener(null).cancel();
            prepareAdjacentTabContent(tabSwipeTarget, tabSwipeDirection);
        }
        applyInteractiveTabDrag(dx);
        return true;
    }

    private void applyInteractiveTabDrag(float dx) {
        float width = tabPagerWidth();
        float maxOffset = width;
        float directionalDx = tabSwipeDirection > 0 ? Math.min(0f, dx) : Math.max(0f, dx);
        float clamped = Math.max(-maxOffset, Math.min(maxOffset, directionalDx));
        tabContent.setTranslationX(clamped);
        tabContent.setAlpha(1f);
        if (tabAdjacentContent != null) {
            tabAdjacentContent.setTranslationX(tabSwipeDirection * width + clamped);
            tabAdjacentContent.setAlpha(1f);
        }
    }

    private void finishInteractiveTabSwipe(MotionEvent event, boolean cancelled) {
        float dx = event == null ? 0f : event.getRawX() - tabSwipeDownX;
        int targetTab = cancelled ? -1 : tabSwipeTarget;
        float width = tabPagerWidth();
        float offset = tabContent == null ? 0f : tabContent.getTranslationX();
        float threshold = Math.min(dp(TAB_SWIPE_MIN_DISTANCE_DP), width * 0.22f);
        boolean shouldSwitch = targetTab >= 0 && Math.abs(offset) >= threshold;
        int direction = tabSwipeDirection;
        resetTabSwipeState();
        if (shouldSwitch) {
            animateInteractiveTabSwitch(targetTab, direction);
        } else {
            animateTabDragBack();
        }
    }

    private void animateTabDragBack() {
        if (tabContent == null) {
            return;
        }
        tabAnimationRunning = true;
        tabContent.animate().setListener(null).cancel();
        if (tabAdjacentContent != null) {
            final float width = tabPagerWidth();
            tabAdjacentContent.animate().setListener(null).cancel();
            tabAdjacentContent.animate()
                    .translationX(tabAdjacentDirection * width)
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(tabEnterInterpolator())
                    .start();
        }
        tabContent.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(180L)
                .setInterpolator(tabEnterInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        clearAdjacentTabContent();
                        finishTabAnimation();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        clearAdjacentTabContent();
                        finishTabAnimation();
                    }
                })
                .start();
    }

    private void animateInteractiveTabSwitch(final int targetTab, final int direction) {
        if (tabContent == null) {
            selectedTab = normalizeTab(targetTab);
            updateTabStyles();
            return;
        }
        final int target = normalizeTab(targetTab);
        final int tabDirection = direction == 0 ? 1 : direction;
        final float width = tabPagerWidth();
        final LinearLayout adjacent = tabAdjacentContent != null
                && tabAdjacentTab == target
                ? tabAdjacentContent
                : prepareAdjacentTabContent(target, tabDirection);
        if (adjacent == null) {
            selectedTab = target;
            updateTabStyles();
            populateTabContent(target);
            finishTabAnimation();
            return;
        }
        tabAnimationRunning = true;
        selectedTab = target;
        updateTabStyles();
        tabContent.animate().setListener(null).cancel();
        adjacent.animate().setListener(null).cancel();
        tabContent.animate()
                .translationX(-tabDirection * width)
                .alpha(1f)
                .setDuration(TAB_ANIMATION_DURATION_MS)
                .setInterpolator(tabEnterInterpolator())
                .start();
        adjacent.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(TAB_ANIMATION_DURATION_MS)
                .setInterpolator(tabEnterInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        promoteAdjacentTabContent();
                        finishTabAnimation();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        finishTabAnimation();
                    }
                })
                .start();
    }

    private int swipeTargetForDx(float dx) {
        if (dx < 0f && selectedTab == TAB_LOCKSCREEN_EFFECT) {
            return TAB_CHARGING_DOODLE;
        }
        if (dx > 0f && selectedTab == TAB_CHARGING_DOODLE) {
            return TAB_LOCKSCREEN_EFFECT;
        }
        return -1;
    }

    private void resetTabSwipeState() {
        tabSwipeStartedOnSlider = false;
        tabSwipeDragging = false;
        tabSwipeTarget = -1;
        tabSwipeDirection = 0;
    }

    private boolean maybeSwitchTabBySwipe(MotionEvent event) {
        if (tabSwipeStartedOnSlider) {
            return false;
        }
        float dx = event.getRawX() - tabSwipeDownX;
        float dy = event.getRawY() - tabSwipeDownY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        if (absX < dp(TAB_SWIPE_MIN_DISTANCE_DP) || absX < absY * TAB_SWIPE_AXIS_RATIO) {
            return false;
        }
        if (dx < 0f && selectedTab == TAB_LOCKSCREEN_EFFECT) {
            showTab(TAB_CHARGING_DOODLE, true, 1);
            return true;
        }
        if (dx > 0f && selectedTab == TAB_CHARGING_DOODLE) {
            showTab(TAB_LOCKSCREEN_EFFECT, true, -1);
            return true;
        }
        return false;
    }

    private boolean isPointInsideViewType(View view, float rawX, float rawY, Class<?> type) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        if (type.isInstance(view) && isPointInsideView(view, rawX, rawY)) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (isPointInsideViewType(group.getChildAt(i), rawX, rawY, type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPointInsideView(View view, float rawX, float rawY) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return rawX >= location[0]
                && rawX <= location[0] + view.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + view.getHeight();
    }

    private void updateTabStyles() {
        styleTabButton(chargingDoodleTabButton, selectedTab == TAB_CHARGING_DOODLE);
        styleTabButton(lockscreenEffectTabButton, selectedTab == TAB_LOCKSCREEN_EFFECT);
    }

    private void styleTabButton(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed},
                solidDrawable(Color.rgb(225, 244, 244), dp(17), Color.TRANSPARENT, 0));
        states.addState(new int[] {}, selected
                ? gradient(GradientDrawable.Orientation.TL_BR,
                        new int[] {Color.WHITE, Color.rgb(225, 248, 247)},
                        dp(17), Color.argb(100, 68, 177, 181), dp(1))
                : solidDrawable(Color.TRANSPARENT, dp(17), Color.TRANSPARENT, 0));
        button.setBackground(states);
        button.setTextColor(selected ? COLOR_ACCENT_DEEP : COLOR_MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(selected ? dp(3) : 0f);
            button.setLetterSpacing(0.08f);
        }
    }

    private void styleCard(LinearLayout section) {
        section.setPadding(dp(18), dp(18), dp(18), dp(22));
        section.setBackground(cardBackground(false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            section.setElevation(dp(3));
        }
    }

    private void styleProfilerCard(LinearLayout section) {
        section.setPadding(dp(24), dp(20), dp(24), dp(20));
        section.setBackground(cardBackground(true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            section.setElevation(0f);
        }
    }

    private void styleInsetPanel(LinearLayout section) {
        section.setPadding(0, dp(6), 0, dp(2));
        section.setBackground(insetPanelBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            section.setElevation(0f);
        }
    }

    private TextView sectionTitle(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(24f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(dp(4), dp(2), 0, dp(14));
        return label;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_ACCENT_DEEP);
        label.setTextSize(12f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setAllCaps(true);
        label.setPadding(0, dp(14), 0, dp(6));
        return label;
    }

    private Switch toggle(String label, final String key, boolean defaultValue) {
        Switch toggle = styledToggle(label, prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, isChecked).apply();
            }
        });
        return toggle;
    }

    private Switch styledToggle(String label, boolean checked) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(COLOR_TEXT);
        toggle.setTextSize(16f);
        toggle.setChecked(checked);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setIncludeFontPadding(false);
        toggle.setPadding(dp(14), 0, dp(8), 0);
        toggle.setBackground(controlRowBackground(false));
        tintSwitch(toggle);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60));
        params.setMargins(0, dp(3), 0, dp(3));
        toggle.setLayoutParams(params);
        return toggle;
    }

    private LinearLayout verticalGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return group;
    }

    private void setRevealState(final View content, boolean visible, boolean animate) {
        content.animate().setListener(null).cancel();
        if (!animate) {
            content.setVisibility(visible ? View.VISIBLE : View.GONE);
            content.setAlpha(1f);
            content.setTranslationY(0f);
            return;
        }
        if (visible) {
            content.setVisibility(View.VISIBLE);
            content.setAlpha(0f);
            content.setTranslationY(-dp(8));
            content.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(tabEnterInterpolator())
                    .start();
            return;
        }
        content.animate()
                .alpha(0f)
                .translationY(-dp(8))
                .setDuration(170L)
                .setInterpolator(tabExitInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (content.getAlpha() <= 0.01f) {
                            content.setVisibility(View.GONE);
                            content.setAlpha(1f);
                            content.setTranslationY(0f);
                        }
                    }
                })
                .start();
    }

    private View toggleWithAutomation(String label, final String key, boolean defaultValue,
            final String timingId, final View timingContent, final View dependentContent) {
        LinearLayout root = verticalGroup();
        LinearLayout bubble = verticalGroup();
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubbleParams.setMargins(0, dp(3), 0, dp(3));
        bubble.setLayoutParams(bubbleParams);
        bubble.setBackground(controlRowBackground(false));

        boolean checked = prefs.getBoolean(key, defaultValue);
        final Switch toggle = styledToggle(label, checked);
        toggle.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54));
        toggle.setLayoutParams(toggleParams);

        final LinearLayout automationRow = new LinearLayout(this);
        automationRow.setOrientation(LinearLayout.HORIZONTAL);
        automationRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        automationRow.setPadding(dp(12), 0, dp(12), dp(6));
        final TextView automationChip = new TextView(this);
        automationChip.setTextColor(COLOR_ACCENT_DEEP);
        automationChip.setTextSize(10f);
        automationChip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        automationChip.setGravity(Gravity.CENTER);
        automationChip.setIncludeFontPadding(false);
        automationChip.setBackground(solidDrawable(
                Color.argb(205, 231, 247, 248),
                dp(13),
                Color.argb(105, 64, 152, 160),
                dp(1)));
        automationRow.addView(automationChip, new LinearLayout.LayoutParams(
                dp(82), dp(27)));

        boolean expanded = expandedTimingSections.contains(timingId);
        updateAutomationChip(automationChip, expanded);
        setRevealState(automationRow, checked, false);
        setRevealState(timingContent, checked && expanded, false);
        if (dependentContent != null) {
            setRevealState(dependentContent, checked, false);
        }

        automationChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean next = !expandedTimingSections.contains(timingId);
                if (next) {
                    expandedTimingSections.add(timingId);
                } else {
                    expandedTimingSections.remove(timingId);
                }
                updateAutomationChip(automationChip, next);
                setRevealState(timingContent, next && toggle.isChecked(), true);
            }
        });
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, isChecked).apply();
                setRevealState(automationRow, isChecked, true);
                setRevealState(timingContent,
                        isChecked && expandedTimingSections.contains(timingId), true);
                if (dependentContent != null) {
                    setRevealState(dependentContent, isChecked, true);
                }
            }
        });

        bubble.addView(toggle);
        bubble.addView(automationRow);
        bubble.addView(timingContent);
        root.addView(bubble);
        if (dependentContent != null) {
            root.addView(dependentContent);
        }
        return root;
    }

    private void updateAutomationChip(TextView chip, boolean expanded) {
        chip.setText(expanded ? "AUTO  \u25BE" : "AUTO  +");
    }

    private Switch invertedToggle(String label, final String key, boolean defaultStoredValue) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(COLOR_TEXT);
        toggle.setTextSize(16f);
        toggle.setChecked(!prefs.getBoolean(key, defaultStoredValue));
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setIncludeFontPadding(false);
        toggle.setPadding(dp(14), 0, dp(8), 0);
        toggle.setBackground(controlRowBackground(false));
        tintSwitch(toggle);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, !isChecked).apply();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60));
        params.setMargins(0, dp(3), 0, dp(3));
        toggle.setLayoutParams(params);
        return toggle;
    }

    private View timeWindowControl(String label, String enabledKey,
            String startKey, String endKey) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        panelParams.setMargins(dp(8), 0, dp(8), dp(7));
        panel.setLayoutParams(panelParams);
        panel.setBackgroundColor(Color.TRANSPARENT);

        final LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setPadding(dp(8), 0, dp(8), dp(7));
        TextView start = timeValueButton("From", startKey);
        TextView end = timeValueButton("Until", endKey);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, dp(48), 1f);
        valueParams.setMargins(dp(3), 0, dp(3), 0);
        times.addView(start, valueParams);
        times.addView(end, valueParams);
        final boolean enabled = prefs.getBoolean(enabledKey, false);
        Switch scheduleToggle = styledToggle(label, enabled);
        scheduleToggle.setTextSize(13f);
        scheduleToggle.setBackgroundColor(Color.TRANSPARENT);
        scheduleToggle.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));
        final String scheduleKey = enabledKey;
        scheduleToggle.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(scheduleKey, isChecked).apply();
                setRevealState(times, isChecked, true);
            }
        });
        panel.addView(scheduleToggle);
        setRevealState(times, enabled, false);
        panel.addView(times);
        return panel;
    }

    private TextView timeValueButton(final String caption, final String key) {
        final TextView button = new TextView(this);
        button.setTextColor(COLOR_ACCENT_DEEP);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setBackground(controlRowBackground(false));
        updateTimeValueButton(button, caption, prefs.getInt(key,
                OverlayPrefs.DEFAULT_TIME_START_MINUTE));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int stored = OverlayPrefs.clampTimeMinute(prefs.getInt(key,
                        OverlayPrefs.DEFAULT_TIME_START_MINUTE));
                TimePickerDialog dialog = new TimePickerDialog(ControlActivity.this,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(android.widget.TimePicker timePicker,
                                    int hourOfDay, int minute) {
                                int value = hourOfDay * 60 + minute;
                                prefs.edit().putInt(key, value).apply();
                                updateTimeValueButton(button, caption, value);
                            }
                        }, stored / 60, stored % 60, true);
                dialog.show();
            }
        });
        return button;
    }

    private void updateTimeValueButton(TextView button, String caption, int minute) {
        int value = OverlayPrefs.clampTimeMinute(minute);
        button.setText(caption + "  " + String.format(Locale.US, "%02d:%02d",
                value / 60, value % 60));
    }

    private View chargingDoodleControls() {
        LinearLayout root = verticalGroup();
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rootParams.setMargins(0, 0, 0, dp(4));
        root.setLayoutParams(rootParams);

        LinearLayout controls = verticalGroup();
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        controlsParams.setMargins(0, 0, 0, dp(12));
        controls.setLayoutParams(controlsParams);
        styleCard(controls);
        controls.addView(sectionTitle("Charging doodle"));

        LinearLayout doodleTiming = verticalGroup();
        doodleTiming.addView(timeWindowControl("Doodle active hours",
                OverlayPrefs.DOODLE_TIME_ENABLED,
                OverlayPrefs.DOODLE_TIME_START,
                OverlayPrefs.DOODLE_TIME_END));

        LinearLayout partnerTiming = verticalGroup();
        partnerTiming.addView(timeWindowControl("Partner effect active hours",
                OverlayPrefs.SEASONAL_UNLOCK_PARTNER_TIME_ENABLED,
                OverlayPrefs.SEASONAL_UNLOCK_PARTNER_TIME_START,
                OverlayPrefs.SEASONAL_UNLOCK_PARTNER_TIME_END));

        LinearLayout partnerSoundTiming = verticalGroup();
        partnerSoundTiming.addView(timeWindowControl("Partner sound active hours",
                OverlayPrefs.SEASONAL_UNLOCK_PARTNER_SOUND_TIME_ENABLED,
                OverlayPrefs.SEASONAL_UNLOCK_PARTNER_SOUND_TIME_START,
                OverlayPrefs.SEASONAL_UNLOCK_PARTNER_SOUND_TIME_END));

        LinearLayout partnerExtras = verticalGroup();
        partnerExtras.addView(toggleWithAutomation("Partner effect sounds",
                OverlayPrefs.SEASONAL_UNLOCK_PARTNER_SOUND_ENABLED,
                true,
                "doodle_partner_sound",
                partnerSoundTiming,
                null));

        LinearLayout doodleLockSoundTiming = verticalGroup();
        doodleLockSoundTiming.addView(timeWindowControl(
                "Doodle lock sound active hours",
                OverlayPrefs.DOODLE_LOCK_SOUND_TIME_ENABLED,
                OverlayPrefs.DOODLE_LOCK_SOUND_TIME_START,
                OverlayPrefs.DOODLE_LOCK_SOUND_TIME_END));

        LinearLayout doodleExtras = verticalGroup();
        doodleExtras.addView(toggleWithAutomation("Seasonal unlock partner",
                OverlayPrefs.SEASONAL_UNLOCK_PARTNER,
                true,
                "doodle_partner",
                partnerTiming,
                partnerExtras));
        doodleExtras.addView(toggleWithAutomation("Doodle lock sound",
                OverlayPrefs.DOODLE_LOCK_SOUND_ENABLED,
                true,
                "doodle_lock_sound",
                doodleLockSoundTiming,
                null));
        controls.addView(toggleWithAutomation("Enable charging doodle",
                OverlayPrefs.SHOW_DOODLE,
                true,
                "doodle",
                doodleTiming,
                doodleExtras));
        root.addView(controls);
        if (FoldDisplayTarget.isFoldDevice(this) && OverlayPrefs.foldModeEnabled(this)) {
            root.addView(foldPanelRoutingControls());
        }
        root.addView(seasonalEffectsCard());
        root.addView(positionControls());
        root.addView(doodleDebugMenu());
        return root;
    }

    private View seasonalEffectsCard() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(sectionParams);
        styleCard(section);
        section.addView(sectionTitle("Seasonal effects"));
        section.addView(effectPreviewHint());
        int currentSeason = prefs.getInt(OverlayPrefs.SEASON_MODE, SeasonalDoodleView.SEASON_AUTO);
        section.addView(seasonalEffectOption(
                "Seasonal auto",
                "Changes automatically with the current season.",
                SeasonalDoodleView.SEASON_AUTO,
                currentSeason));
        section.addView(seasonalEffectOption(
                "Spring",
                "Soft blossoms and fresh spring particles.",
                SeasonalDoodleView.SEASON_SPRING,
                currentSeason));
        section.addView(seasonalEffectOption(
                "Summer",
                "Warm sparks and bright summer colours.",
                SeasonalDoodleView.SEASON_SUMMER,
                currentSeason));
        section.addView(seasonalEffectOption(
                "Autumn",
                "Falling leaves in warm amber tones.",
                SeasonalDoodleView.SEASON_AUTUMN,
                currentSeason));
        section.addView(seasonalEffectOption(
                "Winter",
                "Snowflakes and crisp winter light.",
                SeasonalDoodleView.SEASON_WINTER,
                currentSeason));
        return section;
    }

    private View doodleDebugMenu() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        section.setLayoutParams(params);
        styleCard(section);

        section.addView(collapsibleHeader("Debug", doodleDebugExpanded,
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doodleDebugExpanded = !doodleDebugExpanded;
                showTab(selectedTab);
            }
        }));

        if (doodleDebugExpanded) {
            section.addView(doodleDebugControls());
        }
        return section;
    }

    private TextView collapsibleHeader(String label, boolean expanded,
            View.OnClickListener listener) {
        TextView header = new TextView(this);
        header.setText(expanded ? label + "   -" : label + "   +");
        header.setTextColor(COLOR_ACCENT_DEEP);
        header.setTextSize(16f);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setIncludeFontPadding(false);
        header.setPadding(dp(14), 0, dp(14), 0);
        header.setBackground(controlRowBackground(false));
        header.setOnClickListener(listener);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)));
        return header;
    }

    private View effectSelector() {
        LinearLayout root = verticalGroup();
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rootParams.setMargins(0, 0, 0, dp(4));
        root.setLayoutParams(rootParams);

        LinearLayout controls = verticalGroup();
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        controlsParams.setMargins(0, 0, 0, dp(12));
        controls.setLayoutParams(controlsParams);
        styleCard(controls);
        controls.addView(sectionTitle("Unlock effect"));

        int current = pendingUnlockEffect >= 0 ? pendingUnlockEffect : OverlayPrefs.unlockEffect(this);
        LinearLayout effectTiming = verticalGroup();
        effectTiming.addView(timeWindowControl("Effect active hours",
                OverlayPrefs.UNLOCK_EFFECT_TIME_ENABLED,
                OverlayPrefs.UNLOCK_EFFECT_TIME_START,
                OverlayPrefs.UNLOCK_EFFECT_TIME_END));

        LinearLayout effectSoundTiming = verticalGroup();
        effectSoundTiming.addView(timeWindowControl("Effect sound active hours",
                OverlayPrefs.UNLOCK_EFFECT_SOUND_TIME_ENABLED,
                OverlayPrefs.UNLOCK_EFFECT_SOUND_TIME_START,
                OverlayPrefs.UNLOCK_EFFECT_SOUND_TIME_END));

        LinearLayout lockSoundTiming = verticalGroup();
        lockSoundTiming.addView(timeWindowControl("Lock sound active hours",
                OverlayPrefs.LOCK_SOUND_TIME_ENABLED,
                OverlayPrefs.LOCK_SOUND_TIME_START,
                OverlayPrefs.LOCK_SOUND_TIME_END));

        LinearLayout effectExtras = verticalGroup();
        effectExtras.addView(toggleWithAutomation("Effect sounds",
                OverlayPrefs.UNLOCK_EFFECT_SOUND_ENABLED,
                true,
                "lockscreen_effect_sound",
                effectSoundTiming,
                null));
        effectExtras.addView(toggleWithAutomation("Lock effect sound",
                OverlayPrefs.LOCK_SOUND_ENABLED,
                true,
                "lockscreen_lock_sound",
                lockSoundTiming,
                null));
        controls.addView(toggleWithAutomation("Unlock effect on lockscreen",
                OverlayPrefs.UNLOCK_EFFECT_ENABLED,
                true,
                "lockscreen_effect",
                effectTiming,
                effectExtras));
        root.addView(controls);
        if (FoldDisplayTarget.isFoldDevice(this) && OverlayPrefs.foldModeEnabled(this)) {
            root.addView(foldPanelRoutingControls());
        }

        LinearLayout effects = verticalGroup();
        LinearLayout.LayoutParams effectsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        effectsParams.setMargins(0, 0, 0, dp(12));
        effects.setLayoutParams(effectsParams);
        styleCard(effects);
        effects.addView(sectionTitle("Effects · " + EffectAvailability.processAbiLabel()));
        effects.addView(effectPreviewHint());
        addEffectOptionIfAvailable(effects,
                "S4 Lens Flare",
                "App-owned renderer; no legacy native library required.",
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                current);
        addEffectOptionIfAvailable(effects,
                "S3 Water Ripple",
                EffectAvailability.is64BitProcess()
                        ? "ARM64 app-owned GLES port; transparent local waves over a cached lockscreen colormap."
                        : "Original Samsung ARM32 ripple engine with transparent lockscreen composition.",
                OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE,
                current);
        addEffectOptionIfAvailable(effects,
                "S5 Popping Colours",
                "Samsung dex renderer with screenshot-backed color map; no legacy .so required.",
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                current);
        addEffectOptionIfAvailable(effects,
                "N3 Watercolor",
                EffectAvailability.is64BitProcess()
                        ? "ARM64 GLES port of Samsung Watercolor with a transparent screenshot-backed brush."
                        : "Original Samsung ARM32 Watercolor engine with transparent lockscreen composition.",
                OverlayPrefs.EFFECT_WATERCOLOUR,
                current);
        if (EffectAvailability.isAvailable(OverlayPrefs.EFFECT_S4_ABSTRACT_TILES)) {
            if (EffectAvailability.is64BitProcess()) {
                boolean currentLineMode = pendingUnlockEffect
                        == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                        && pendingAbstractTilesLineMode >= 0
                        ? pendingAbstractTilesLineMode == 1
                        : OverlayPrefs.abstractTilesLineEnabled(this);
                effects.addView(abstractTilesEffectOption(
                        "N4 Abstract Tiles · Lines",
                        "Tile and Scatter animation with the recovered Line layer.",
                        true,
                        current,
                        currentLineMode));
                effects.addView(abstractTilesEffectOption(
                        "N4 Abstract Tiles · No lines",
                        "Tiles-only variant; Line shader, mask and draw pass stay disabled.",
                        false,
                        current,
                        currentLineMode));
            } else {
                effects.addView(effectOption(
                        "N4 Abstract Tiles",
                        "Original Samsung ARM32 LockBG tile renderer with transparent screenshot composition.",
                        OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                        current));
            }
        }
        addEffectOptionIfAvailable(effects,
                "N4 Geometric Mosaic",
                EffectAvailability.is64BitProcess()
                        ? "Reconstructed ARM64 mosaic renderer with transparent screenshot composition."
                        : "Original ARM32 LockBG mosaic renderer with transparent screenshot composition.",
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC,
                current);
        addEffectOptionIfAvailable(effects,
                "N5 Colored Droplet",
                EffectAvailability.is64BitProcess()
                        ? "Original Note 5 ARM64 renderer; the live lockscreen is sampled only inside droplets."
                        : "Original Samsung ARM32 droplet renderer with transparent lockscreen sampling.",
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                current);
        addEffectOptionIfAvailable(effects,
                "N5 Colored Droplet + Gyro",
                EffectAvailability.is64BitProcess()
                        ? "Original Note 5 ARM64 renderer with accelerometer-driven gravity."
                        : "Original Samsung ARM32 droplet physics with accelerometer-driven gravity.",
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                current);
        addEffectOptionIfAvailable(effects,
                "N5 Sparkling Bubbles",
                EffectAvailability.is64BitProcess()
                        ? "Original Note 5 ARM64 renderer; the live lockscreen colors only the particles."
                        : "Original Samsung ARM32 bubbles renderer with cached lockscreen color sampling.",
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                current);

        root.addView(effects);
        root.addView(screenshotServiceControls(current));
        return root;
    }

    private void addEffectOptionIfAvailable(LinearLayout effects, String title,
            String description, int effect, int current) {
        if (!EffectAvailability.isAvailable(effect)) {
            return;
        }
        effects.addView(effectOption(title, description, effect, current));
    }

    private View effectPreviewHint() {
        TextView hint = new TextView(this);
        hint.setText("Hold an effect icon for preview");
        hint.setTextColor(Color.rgb(69, 83, 103));
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(dp(14), 0, dp(14), 0);
        hint.setBackground(gradient(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {
                        Color.argb(175, 221, 248, 244),
                        Color.argb(130, 241, 225, 249),
                        Color.argb(105, 255, 236, 197)
                },
                dp(15),
                Color.argb(80, 105, 181, 183),
                dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44));
        params.setMargins(0, 0, 0, dp(10));
        hint.setLayoutParams(params);
        return hint;
    }

    private View screenshotServiceControls(final int currentEffect) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(sectionTitle("Screenshot service"));
        section.addView(infoText(effectBackgroundStatus(currentEffect)));
        section.addView(outlineButton("Force screenshot recapture", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyPendingUnlockEffect();
                OverlayPrefs.requestEffectBackgroundRefresh(ControlActivity.this);
                Toast.makeText(ControlActivity.this,
                        "Screenshot recapture queued",
                        Toast.LENGTH_SHORT).show();
            }
        }));
        section.addView(outlineButton(OverlayPrefs.foldModeEnabled(this)
                ? "View both panel screenshots"
                : "View colormap screenshot", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyPendingUnlockEffect();
                showEffectBackgroundScreenshot();
            }
        }));
        return section;
    }

    private View screenshotServiceDebugControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(8));
        section.setLayoutParams(params);
        styleInsetPanel(section);
        section.addView(sectionLabel("Screenshot service"));
        section.addView(toggle("Auto recapture expired cache",
                OverlayPrefs.EFFECT_BACKGROUND_AUTO_REFRESH_ENABLED, false));
        section.addView(effectBackgroundIntervalSelector());
        section.addView(toggle("Pause auto recapture 23-07",
                OverlayPrefs.EFFECT_BACKGROUND_SKIP_NIGHT, true));
        section.addView(toggle("Wake lockscreen for hard recapture",
                OverlayPrefs.EFFECT_BACKGROUND_FORCE_RECAPTURE, false));
        return section;
    }

    private void queueUnlockEffectSelection(int value) {
        queueUnlockEffectSelection(value, -1);
    }

    private void queueAbstractTilesSelection(boolean lineEnabled) {
        queueUnlockEffectSelection(
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                lineEnabled ? 1 : 0);
    }

    private void queueUnlockEffectSelection(int value, int abstractTilesLineMode) {
        pendingUnlockEffect = value;
        pendingAbstractTilesLineMode = abstractTilesLineMode;
        uiHandler.removeCallbacks(applyPendingUnlockEffectRunnable);
        uiHandler.postDelayed(applyPendingUnlockEffectRunnable,
                EFFECT_SELECTION_APPLY_DELAY_MS);
        Toast.makeText(this, "Effect will apply in 2s", Toast.LENGTH_SHORT).show();
    }

    private void applyPendingUnlockEffect() {
        if (pendingUnlockEffect < 0) {
            return;
        }
        int effect = pendingUnlockEffect;
        int abstractTilesLineMode = pendingAbstractTilesLineMode;
        pendingUnlockEffect = -1;
        pendingAbstractTilesLineMode = -1;
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(OverlayPrefs.UNLOCK_EFFECT, effect);
        if (effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                && abstractTilesLineMode >= 0) {
            editor.putBoolean(
                    OverlayPrefs.ABSTRACT_TILES_LINE_ENABLED,
                    abstractTilesLineMode == 1);
        }
        editor.apply();
        showTab(TAB_LOCKSCREEN_EFFECT);
    }

    private String effectBackgroundStatus(int effect) {
        if (!effectUsesColormapCache(effect)) {
            return "Screenshot cache: not used by this effect.";
        }
        String profile = FoldDisplayTarget.cacheProfileForContext(this);
        if (OverlayPrefs.foldModeEnabled(this)) {
            return effectBackgroundProfileStatus(effect, FoldDisplayTarget.PROFILE_COVER)
                    + "\n"
                    + effectBackgroundProfileStatus(effect, FoldDisplayTarget.PROFILE_MAIN)
                    + "\nActive panel: " + profile;
        }
        return effectBackgroundProfileStatus(effect, profile);
    }

    private String effectBackgroundProfileStatus(int effect, String profile) {
        File file = colormapScreenshotFileForPreview(effect, profile);
        long capturedAt = OverlayPrefs.effectBackgroundLastCapturedAt(this, effect, profile);
        if (capturedAt <= 0L && file.exists()) {
            capturedAt = file.lastModified();
        }
        if (!file.exists() || file.length() <= 0L || capturedAt <= 0L) {
            return "Screenshot cache (" + profile + "): empty.";
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - capturedAt);
        return "Screenshot cache (" + profile + "): ready, age " + ageLabel(ageMs)
                + ". Expired cache stays active until a validated capture replaces it.";
    }

    private boolean effectUsesColormapCache(int effect) {
        return effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC;
    }

    private String ageLabel(long ageMs) {
        long minutes = ageMs / 60000L;
        if (minutes < 1L) {
            return "now";
        }
        if (minutes < 60L) {
            return minutes + "m";
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return hours + "h";
        }
        return (hours / 24L) + "d";
    }

    private View effectBackgroundIntervalSelector() {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        group.setPadding(0, dp(4), 0, dp(4));
        int current = OverlayPrefs.effectBackgroundRefreshIntervalHours(this);
        addIntervalOption(group, "8h", 8, current);
        addIntervalOption(group, "24h", 24, current);
        addIntervalOption(group, "7d", 168, current);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                View checked = radioGroup.findViewById(checkedId);
                Object tag = checked == null ? null : checked.getTag();
                if (tag instanceof Integer) {
                    prefs.edit()
                            .putInt(OverlayPrefs.EFFECT_BACKGROUND_REFRESH_INTERVAL_HOURS,
                                    ((Integer) tag).intValue())
                            .apply();
                }
                for (int i = 0; i < radioGroup.getChildCount(); i++) {
                    View child = radioGroup.getChildAt(i);
                    if (child instanceof RadioButton) {
                        child.setBackground(controlRowBackground(((RadioButton) child).isChecked()));
                    }
                }
            }
        });
        return group;
    }

    private void addIntervalOption(RadioGroup group, String label, int hours, int current) {
        RadioButton button = new RadioButton(this);
        button.setText(label);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(15f);
        button.setTag(Integer.valueOf(hours));
        button.setId(View.generateViewId());
        button.setChecked(hours == current);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(controlRowBackground(hours == current));
        tintRadio(button);
        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                0,
                dp(44),
                1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        group.addView(button, params);
    }

    private View effectProfilerControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(8));
        section.setLayoutParams(params);
        styleInsetPanel(section);

        TextView eyebrow = sectionLabel("Automatic runtime sample");
        eyebrow.setPadding(0, 0, 0, dp(2));
        section.addView(eyebrow);

        TextView title = sectionTitle("Effect memory");
        title.setPadding(0, 0, 0, dp(4));
        section.addView(title);

        effectProfilerSummary = new TextView(this);
        effectProfilerSummary.setTextColor(COLOR_TEXT);
        effectProfilerSummary.setTextSize(14f);
        effectProfilerSummary.setLineSpacing(dp(3), 1.0f);
        effectProfilerSummary.setIncludeFontPadding(false);
        effectProfilerSummary.setPadding(dp(12), dp(10), dp(12), dp(10));
        effectProfilerSummary.setBackground(infoBackground());
        section.addView(effectProfilerSummary);
        updateEffectProfilerSummary();

        section.addView(outlineButton("Sample next run", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestEffectProfileResample();
            }
        }));
        return section;
    }

    private void requestEffectProfileResample() {
        int effect = OverlayPrefs.unlockEffect(this);
        String effectName = OverlayPrefs.effectLabel(effect);
        prefs.edit()
                .remove(OverlayPrefs.effectProfileSampledTokenKey(effect))
                .putBoolean(OverlayPrefs.EFFECT_PROFILE_SAMPLE_PENDING, true)
                .putBoolean(OverlayPrefs.EFFECT_PROFILE_RUNNING, false)
                .putString(OverlayPrefs.EFFECT_PROFILE_LAST_SUMMARY,
                        "Next " + effectName + " run will be sampled."
                                + "\nLock the phone and trigger the effect once.")
                .apply();
        updateEffectProfilerSummary();
        Toast.makeText(this, "Next effect run will be sampled", Toast.LENGTH_SHORT).show();
    }

    private void updateEffectProfilerSummary() {
        if (effectProfilerSummary == null || prefs == null) {
            return;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        int token = OverlayPrefs.effectProfileSampleToken(this);
        int sampledToken = prefs.getInt(
                OverlayPrefs.effectProfileSampledTokenKey(effect),
                Integer.MIN_VALUE);
        String effectName = OverlayPrefs.effectLabel(effect);
        String summary = prefs.getString(OverlayPrefs.EFFECT_PROFILE_LAST_SUMMARY, "");
        boolean running = prefs.getBoolean(OverlayPrefs.EFFECT_PROFILE_RUNNING, false);
        if (!isEffectProfileCardSummary(summary)) {
            if (summary != null && !summary.trim().isEmpty()) {
                prefs.edit().remove(OverlayPrefs.EFFECT_PROFILE_LAST_SUMMARY).apply();
            }
            summary = "";
        }
        if (summary == null || summary.trim().isEmpty()) {
            summary = effectName
                    + "\nNo real-run sample yet."
                    + "\nTap Sample next run to profile one lockscreen gesture.";
        }
        if (sampledToken != token) {
            summary += "\nPending sample: next " + effectName + " run.";
        } else {
            summary += "\nSampling is idle. Use the button to refresh on the next run.";
        }
        if (running) {
            summary = "ADB diagnostic benchmark running...\n" + summary;
        }
        effectProfilerSummary.setText(summary);
    }

    private boolean isEffectProfileCardSummary(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            return true;
        }
        return summary.contains("Sampled on unlock effect run")
                || summary.startsWith("Next ");
    }

    private void showEffectBackgroundScreenshot() {
        int effect = OverlayPrefs.unlockEffect(this);
        if (OverlayPrefs.foldModeEnabled(this)) {
            showFoldEffectBackgroundScreenshots(effect);
            return;
        }
        File screenshot = colormapScreenshotFileForPreview(effect);
        if (!screenshot.exists() || screenshot.length() <= 0L) {
            Toast.makeText(this, "No colormap screenshot yet", Toast.LENGTH_SHORT).show();
            return;
        }
        final Bitmap bitmap = decodePreviewBitmap(screenshot);
        if (bitmap == null || bitmap.isRecycled()) {
            Toast.makeText(this, "Colormap screenshot unreadable", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackground(pageBackground());

        TextView title = sectionTitle("Colormap screenshot");
        root.addView(title);

        TextView meta = infoText("service shared | "
                + bitmap.getWidth() + " x " + bitmap.getHeight()
                + " | " + Math.max(1L, screenshot.length() / 1024L) + " KB");
        root.addView(meta);

        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        imageParams.setMargins(0, dp(8), 0, dp(10));
        root.addView(image, imageParams);

        root.addView(outlineButton("Close", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        }));

        dialog.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        });
        dialog.show();
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void showFoldEffectBackgroundScreenshots(int effect) {
        final ArrayList<Bitmap> previews = new ArrayList<Bitmap>();
        File cover = colormapScreenshotFileForPreview(effect, FoldDisplayTarget.PROFILE_COVER);
        File main = colormapScreenshotFileForPreview(effect, FoldDisplayTarget.PROFILE_MAIN);
        if ((!cover.exists() || cover.length() <= 0L)
                && (!main.exists() || main.length() <= 0L)) {
            Toast.makeText(this, "No Fold screenshots yet", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackground(pageBackground());
        root.addView(sectionTitle("Fold colormap screenshots"));
        root.addView(infoText("The cover and main caches are independent. Missing panels are shown explicitly."));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        rowParams.setMargins(0, dp(10), 0, dp(10));
        root.addView(row, rowParams);
        row.addView(foldScreenshotPreview("Cover", cover, previews),
                foldScreenshotColumnParams(false));
        row.addView(foldScreenshotPreview("Main", main, previews),
                foldScreenshotColumnParams(true));

        root.addView(outlineButton("Close", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        }));
        dialog.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                for (Bitmap bitmap : previews) {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            }
        });
        dialog.show();
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private View foldScreenshotPreview(String label, File file, ArrayList<Bitmap> previews) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(10), dp(10), dp(10), dp(10));
        column.setBackground(infoBackground());
        column.addView(sectionLabel(label + " panel"));
        if (file == null || !file.exists() || file.length() <= 0L) {
            TextView missing = infoText("No screenshot cached");
            missing.setGravity(Gravity.CENTER);
            column.addView(missing, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return column;
        }
        Bitmap bitmap = decodeFoldPreviewBitmap(file);
        if (bitmap == null || bitmap.isRecycled()) {
            TextView missing = infoText("Screenshot unreadable");
            missing.setGravity(Gravity.CENTER);
            column.addView(missing, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return column;
        }
        previews.add(bitmap);
        column.addView(infoText(bitmap.getWidth() + " x " + bitmap.getHeight()
                + " preview | " + Math.max(1L, file.length() / 1024L) + " KB"));
        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        imageParams.setMargins(0, dp(8), 0, 0);
        column.addView(image, imageParams);
        return column;
    }

    private LinearLayout.LayoutParams foldScreenshotColumnParams(boolean withLeftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        if (withLeftMargin) {
            params.setMargins(dp(8), 0, 0, 0);
        }
        return params;
    }

    private void showEffectPreviewBubble(View anchor, int effect, String title,
            float rawX, float rawY) {
        showPreviewBubble(anchor, title, rawX, rawY,
                createEffectPreviewBitmap(effect, 720, 720), true);
    }

    private void showSeasonPreviewBubble(View anchor, int season, String title,
            float rawX, float rawY) {
        showPreviewBubble(anchor, title, rawX, rawY,
                createDoodlePreviewBitmap(420, 720, season), false);
    }

    private void showPreviewBubble(View anchor, String title, float rawX, float rawY,
            Bitmap previewBitmap, boolean squarePreview) {
        if (anchor == null || isFinishing()) {
            if (previewBitmap != null && !previewBitmap.isRecycled()) {
                previewBitmap.recycle();
            }
            return;
        }
        hideEffectPreviewBubble();

        effectPreviewPopupBitmap = previewBitmap;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int popupWidth = Math.min(Math.max(dp(260), screenWidth - dp(64)), dp(300));
        // Root and card horizontal padding consume 28dp in total. Real effect
        // captures are 1:1; seasonal placeholders retain their original portrait ratio.
        int imageHeight = squarePreview ? popupWidth - dp(28) : dp(240);
        int estimatedHeight = imageHeight + dp(70);

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int anchorCenterX = Math.round(rawX > 0f
                ? rawX
                : anchorLocation[0] + anchor.getWidth() * 0.5f);
        int anchorTop = anchorLocation[1];
        int anchorBottom = anchorLocation[1] + anchor.getHeight();
        boolean showAbove = anchorTop > estimatedHeight + dp(22);

        int left = clampInt(anchorCenterX - popupWidth / 2,
                dp(12),
                Math.max(dp(12), screenWidth - popupWidth - dp(12)));
        int top = showAbove
                ? anchorTop - estimatedHeight + dp(6)
                : anchorBottom - dp(6);
        top = clampInt(top, dp(8), Math.max(dp(8), screenHeight - estimatedHeight - dp(8)));
        int tailLeft = clampInt(anchorCenterX - left - dp(18), dp(22), popupWidth - dp(56));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(2), 0, dp(2), 0);
        root.setClipToPadding(false);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(12));
        card.setBackground(solidDrawable(COLOR_SURFACE, dp(20),
                Color.argb(110, 0, 132, 142), dp(1)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(10));
        }

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(16f);
        titleView.setSingleLine(true);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, dp(8));
        card.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView image = new ImageView(this);
        image.setImageBitmap(effectPreviewPopupBitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(solidDrawable(Color.rgb(245, 248, 250), dp(14),
                Color.TRANSPARENT, 0));
        card.addView(image, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                imageHeight));

        View tail = previewBubbleTail(showAbove);
        LinearLayout.LayoutParams tailParams = new LinearLayout.LayoutParams(dp(36), dp(17));
        tailParams.setMargins(tailLeft, 0, 0, 0);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (showAbove) {
            root.addView(card, cardParams);
            root.addView(tail, tailParams);
        } else {
            root.addView(tail, tailParams);
            root.addView(card, cardParams);
        }

        forceSansSerif(root);
        root.setAlpha(0f);
        root.setScaleX(0.96f);
        root.setScaleY(0.96f);
        root.setPivotX(anchorCenterX - left);
        root.setPivotY(showAbove ? estimatedHeight : 0f);

        effectPreviewPopup = new PopupWindow(root,
                popupWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                false);
        effectPreviewPopup.setTouchable(false);
        effectPreviewPopup.setOutsideTouchable(false);
        effectPreviewPopup.setClippingEnabled(false);
        effectPreviewPopup.setBackgroundDrawable(
                solidDrawable(Color.TRANSPARENT, 0, Color.TRANSPARENT, 0));
        effectPreviewPopup.showAtLocation(getWindow().getDecorView(),
                Gravity.NO_GRAVITY,
                left,
                top);
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .setInterpolator(tabEnterInterpolator())
                .start();
    }

    private void hideEffectPreviewBubble() {
        if (effectPreviewPopup != null) {
            effectPreviewPopup.dismiss();
            effectPreviewPopup = null;
        }
        if (effectPreviewPopupBitmap != null && !effectPreviewPopupBitmap.isRecycled()) {
            effectPreviewPopupBitmap.recycle();
        }
        effectPreviewPopupBitmap = null;
    }

    private View previewBubbleTail(final boolean pointsDown) {
        View tail = new View(this) {
            private final Paint tailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float width = getWidth();
                float height = getHeight();
                Path path = new Path();
                if (pointsDown) {
                    path.moveTo(0f, 0f);
                    path.lineTo(width, 0f);
                    path.lineTo(width * 0.5f, height);
                } else {
                    path.moveTo(0f, height);
                    path.lineTo(width, height);
                    path.lineTo(width * 0.5f, 0f);
                }
                path.close();
                tailPaint.setStyle(Paint.Style.FILL);
                tailPaint.setColor(COLOR_SURFACE);
                canvas.drawPath(path, tailPaint);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(dp(1));
                strokePaint.setColor(Color.argb(90, 0, 132, 142));
                canvas.drawPath(path, strokePaint);
            }
        };
        tail.setWillNotDraw(false);
        return tail;
    }

    private Bitmap createDoodlePreviewBitmap(int width, int height, int seasonMode) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        drawGracePreviewBackground(canvas, paint, width, height, true);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(68, 0, 0, 0));
        canvas.drawRoundRect(width * 0.06f, height * 0.12f,
                width * 0.94f, height * 0.88f, dp(18), dp(18), paint);

        drawDoodleSeasonalParticles(canvas, paint, width, height,
                resolveDoodlePreviewSeason(seasonMode));
        drawDoodleChargingMark(canvas, paint, width, height);

        paint.setShader(null);
        paint.setColor(Color.WHITE);
        paint.setTypeface(appTypeface(Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(height * 0.22f);
        canvas.drawText("10:54", width * 0.5f, height * 0.45f, paint);

        paint.setTextSize(height * 0.055f);
        paint.setColor(Color.argb(210, 255, 255, 255));
        canvas.drawText("Charging doodle", width * 0.5f, height * 0.70f, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(height * 0.055f);
        canvas.drawText("97%", width * 0.73f, height * 0.20f, paint);
        drawBatteryGlyph(canvas, paint, width * 0.84f, height * 0.155f,
                width * 0.095f, height * 0.045f);
        paint.setTextAlign(Paint.Align.LEFT);
        return bitmap;
    }

    private Bitmap createEffectPreviewBitmap(int effect, int width, int height) {
        Bitmap stockPreview = decodeStockEffectPreview(effect);
        if (stockPreview != null) {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
            drawStockEffectPreview(canvas, paint, stockPreview, width, height);
            if (!stockPreview.isRecycled()) {
                stockPreview.recycle();
            }
            return bitmap;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        drawGracePreviewBackground(canvas, paint, width, height, false);

        switch (effect) {
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                drawPreviewLensFlare(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
                drawPreviewRipple(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                drawPreviewPoppingColours(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                drawPreviewWatercolor(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
                drawPreviewDroplets(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
                drawPreviewBubbles(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                drawPreviewTiles(canvas, paint, width, height, false);
                break;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                drawPreviewTiles(canvas, paint, width, height, true);
                break;
            default:
                drawPreviewLensFlare(canvas, paint, width, height);
                break;
        }
        drawPreviewVignette(canvas, paint, width, height);
        return bitmap;
    }

    private Bitmap decodeStockEffectPreview(int effect) {
        int resId = stockEffectPreviewResId(effect);
        if (resId == 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(getResources(), resId, options);
    }

    private int stockEffectPreviewResId(int effect) {
        switch (effect) {
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
                return R.drawable.preview_unlock_s3_ripple_lle;
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                return R.drawable.preview_unlock_s4_lens_flare_lle;
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                return R.drawable.preview_unlock_n3_watercolor_lle;
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                return R.drawable.preview_unlock_s5_popping_colours_lle;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return R.drawable.preview_unlock_n4_abstract_tiles_lle;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return R.drawable.preview_unlock_n4_geometric_mosaic_lle;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
                return R.drawable.preview_unlock_n5_colored_droplet_lle;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
                return R.drawable.preview_unlock_n5_colored_droplet_gyro_lle;
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
                return R.drawable.preview_unlock_n5_sparkling_bubbles_lle;
            default:
                return 0;
        }
    }

    private int effectIconResId(int effect) {
        switch (effect) {
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
                return R.drawable.icon_effect_s3_ripple_lle;
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                return R.drawable.icon_effect_s4_lens_flare_lle;
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                return R.drawable.icon_effect_n3_watercolor_lle;
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                return R.drawable.icon_effect_s5_popping_colours_lle;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return R.drawable.icon_effect_n4_abstract_tiles_lle;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return R.drawable.icon_effect_n4_geometric_mosaic_lle;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
                return R.drawable.icon_effect_n5_colored_droplet_lle;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
                return R.drawable.icon_effect_n5_colored_droplet_gyro_lle;
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
                return R.drawable.icon_effect_n5_sparkling_bubbles_lle;
            case OverlayPrefs.EFFECT_N3_INK_IN_WATER_WIP:
                return R.drawable.icon_effect_n3_ink_in_water_lle;
            default:
                return 0;
        }
    }

    private void drawStockEffectPreview(Canvas canvas, Paint paint, Bitmap source,
            int width, int height) {
        float scale = Math.max(width / (float) source.getWidth(),
                height / (float) source.getHeight());
        int drawWidth = Math.round(source.getWidth() * scale);
        int drawHeight = Math.round(source.getHeight() * scale);
        int left = (width - drawWidth) / 2;
        int top = (height - drawHeight) / 2;
        Rect dst = new Rect(left, top, left + drawWidth, top + drawHeight);
        canvas.drawBitmap(source, null, dst, paint);
    }

    private void drawGracePreviewBackground(Canvas canvas, Paint paint, int width, int height,
            boolean darker) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                0f,
                0f,
                width,
                height,
                new int[] {
                        Color.rgb(82, 69, 165),
                        Color.rgb(42, 144, 197),
                        Color.rgb(139, 207, 181)
                },
                new float[] {0f, 0.52f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        Path plane = new Path();
        plane.moveTo(width * 0.34f, 0f);
        plane.lineTo(width, 0f);
        plane.lineTo(width, height * 0.46f);
        plane.lineTo(width * 0.48f, height * 0.75f);
        plane.close();
        paint.setColor(Color.argb(142, 226, 245, 226));
        canvas.drawPath(plane, paint);

        plane.reset();
        plane.moveTo(0f, height * 0.38f);
        plane.lineTo(width * 0.47f, height * 0.76f);
        plane.lineTo(width, height * 0.48f);
        plane.lineTo(width, height);
        plane.lineTo(0f, height);
        plane.close();
        paint.setColor(Color.argb(126, 21, 54, 92));
        canvas.drawPath(plane, paint);

        plane.reset();
        plane.moveTo(0f, 0f);
        plane.lineTo(width * 0.39f, 0f);
        plane.lineTo(width * 0.22f, height * 0.58f);
        plane.lineTo(0f, height * 0.42f);
        plane.close();
        paint.setColor(Color.argb(122, 164, 58, 236));
        canvas.drawPath(plane, paint);

        if (darker) {
            paint.setColor(Color.argb(72, 0, 0, 0));
            canvas.drawRect(0, 0, width, height, paint);
        }
    }

    private void drawPreviewLensFlare(Canvas canvas, Paint paint, int width, int height) {
        float cx = width * 0.55f;
        float cy = height * 0.52f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 6; i >= 1; i--) {
            paint.setStrokeWidth(dp(1) + i);
            paint.setColor(Color.argb(18 + i * 18, 255, 228, 120));
            float radius = width * (0.055f + i * 0.036f);
            canvas.drawCircle(cx, cy, radius, paint);
        }

        paint.setStrokeWidth(dp(3));
        paint.setColor(Color.argb(148, 255, 246, 190));
        canvas.drawLine(cx - width * 0.32f, cy, cx + width * 0.32f, cy, paint);
        canvas.drawLine(cx, cy - height * 0.34f, cx, cy + height * 0.34f, paint);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(118, 255, 184, 92));
        canvas.drawLine(cx - width * 0.23f, cy + height * 0.18f,
                cx + width * 0.22f, cy - height * 0.18f, paint);

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 5; i++) {
            paint.setColor(Color.argb(120 - i * 14, 255, 230, 120));
            canvas.drawCircle(width * (0.20f + i * 0.13f),
                    height * (0.68f - i * 0.06f),
                    width * (0.018f + i * 0.004f),
                    paint);
        }
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, width * 0.032f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPreviewRipple(Canvas canvas, Paint paint, int width, int height) {
        float cx = width * 0.48f;
        float cy = height * 0.58f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 8; i++) {
            paint.setStrokeWidth(dp(2) + i * 0.9f);
            paint.setColor(Color.argb(160 - i * 15, 170, 235, 255));
            canvas.drawCircle(cx, cy, width * (0.06f + i * 0.052f), paint);
        }
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(90, 255, 255, 255));
        canvas.drawLine(width * 0.18f, height * 0.62f,
                width * 0.82f, height * 0.43f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(210, 230, 252, 255));
        canvas.drawCircle(cx, cy, width * 0.025f, paint);
    }

    private void drawPreviewPoppingColours(Canvas canvas, Paint paint, int width, int height) {
        int[] colors = {
                Color.rgb(255, 111, 92),
                Color.rgb(255, 195, 72),
                Color.rgb(96, 212, 108),
                Color.rgb(84, 186, 246),
                Color.rgb(172, 102, 230),
                Color.rgb(246, 105, 172)
        };
        float[] xs = {0.27f, 0.42f, 0.58f, 0.70f, 0.36f, 0.53f, 0.66f};
        float[] ys = {0.62f, 0.44f, 0.59f, 0.39f, 0.72f, 0.72f, 0.66f};
        float[] rs = {0.10f, 0.075f, 0.13f, 0.085f, 0.055f, 0.065f, 0.045f};
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < xs.length; i++) {
            paint.setColor(Color.argb(210, Color.red(colors[i % colors.length]),
                    Color.green(colors[i % colors.length]), Color.blue(colors[i % colors.length])));
            canvas.drawCircle(width * xs[i], height * ys[i], width * rs[i], paint);
            paint.setColor(Color.argb(78, 255, 255, 255));
            canvas.drawCircle(width * (xs[i] - 0.025f), height * (ys[i] - 0.035f),
                    width * rs[i] * 0.28f, paint);
        }
    }

    private void drawPreviewWatercolor(Canvas canvas, Paint paint, int width, int height) {
        int[] colors = {
                Color.argb(120, 99, 220, 211),
                Color.argb(132, 130, 111, 238),
                Color.argb(118, 255, 135, 166),
                Color.argb(108, 255, 213, 95)
        };
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 9; i++) {
            paint.setColor(colors[i % colors.length]);
            float cx = width * (0.22f + (i % 4) * 0.16f);
            float cy = height * (0.39f + (i / 4) * 0.16f + (i % 2) * 0.05f);
            canvas.drawCircle(cx, cy, width * (0.075f + (i % 3) * 0.018f), paint);
        }
        Path wash = new Path();
        wash.moveTo(width * 0.30f, height * 0.62f);
        wash.cubicTo(width * 0.38f, height * 0.38f, width * 0.72f, height * 0.38f,
                width * 0.78f, height * 0.58f);
        wash.cubicTo(width * 0.70f, height * 0.78f, width * 0.42f, height * 0.80f,
                width * 0.30f, height * 0.62f);
        paint.setColor(Color.argb(98, 255, 255, 255));
        canvas.drawPath(wash, paint);
    }

    private void drawPreviewDroplets(Canvas canvas, Paint paint, int width, int height) {
        drawDroplet(canvas, paint, width * 0.36f, height * 0.52f,
                width * 0.0021f, Color.argb(210, 255, 96, 103));
        drawDroplet(canvas, paint, width * 0.52f, height * 0.45f,
                width * 0.0017f, Color.argb(210, 92, 219, 201));
        drawDroplet(canvas, paint, width * 0.64f, height * 0.60f,
                width * 0.00195f, Color.argb(210, 255, 206, 88));
    }

    private void drawDroplet(Canvas canvas, Paint paint, float cx, float cy, float scale,
            int color) {
        float s = scale;
        Path droplet = new Path();
        droplet.moveTo(cx, cy - 58f * s);
        droplet.cubicTo(cx + 60f * s, cy - 8f * s, cx + 52f * s, cy + 70f * s,
                cx, cy + 84f * s);
        droplet.cubicTo(cx - 52f * s, cy + 70f * s, cx - 60f * s, cy - 8f * s,
                cx, cy - 58f * s);
        droplet.close();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(droplet, paint);
        paint.setColor(Color.argb(92, 255, 255, 255));
        canvas.drawCircle(cx - 16f * s, cy - 6f * s, 12f * s, paint);
    }

    private void drawPreviewBubbles(Canvas canvas, Paint paint, int width, int height) {
        float[] xs = {0.25f, 0.38f, 0.50f, 0.62f, 0.73f, 0.31f, 0.56f};
        float[] ys = {0.62f, 0.43f, 0.70f, 0.50f, 0.64f, 0.78f, 0.32f};
        float[] rs = {0.060f, 0.045f, 0.075f, 0.052f, 0.042f, 0.034f, 0.030f};
        paint.setShader(null);
        for (int i = 0; i < xs.length; i++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(45, 255, 255, 255));
            canvas.drawCircle(width * xs[i], height * ys[i], width * rs[i], paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(150, 206, 255, 255));
            canvas.drawCircle(width * xs[i], height * ys[i], width * rs[i], paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPreviewTiles(Canvas canvas, Paint paint, int width, int height,
            boolean geometric) {
        int[] colors = {
                Color.argb(190, 255, 188, 80),
                Color.argb(190, 94, 207, 226),
                Color.argb(190, 173, 103, 225),
                Color.argb(190, 106, 210, 116)
        };
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 6; col++) {
                float left = width * (0.16f + col * 0.115f);
                float top = height * (0.26f + row * 0.13f);
                float size = width * 0.08f;
                paint.setColor(colors[(row + col) % colors.length]);
                if (geometric) {
                    Path piece = new Path();
                    piece.moveTo(left + size * 0.5f, top);
                    piece.lineTo(left + size, top + size * 0.45f);
                    piece.lineTo(left + size * 0.56f, top + size);
                    piece.lineTo(left, top + size * 0.52f);
                    piece.close();
                    canvas.drawPath(piece, paint);
                } else {
                    canvas.save();
                    canvas.rotate(-8f + (row + col) * 3f, left + size * 0.5f,
                            top + size * 0.5f);
                    canvas.drawRoundRect(left, top, left + size, top + size,
                            dp(8), dp(8), paint);
                    canvas.restore();
                }
            }
        }
    }

    private void drawPreviewVignette(Canvas canvas, Paint paint, int width, int height) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(36, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setColor(Color.argb(58, 255, 255, 255));
        canvas.drawRoundRect(width * 0.035f, height * 0.055f,
                width * 0.965f, height * 0.945f, dp(22), dp(22), paint);
        paint.setColor(Color.argb(32, 0, 0, 0));
        canvas.drawRoundRect(width * 0.055f, height * 0.075f,
                width * 0.945f, height * 0.925f, dp(18), dp(18), paint);
    }

    private int resolveDoodlePreviewSeason(int mode) {
        if (mode >= SeasonalDoodleView.SEASON_SPRING
                && mode <= SeasonalDoodleView.SEASON_WINTER) {
            return mode;
        }
        int month = Calendar.getInstance().get(Calendar.MONTH);
        if (month >= Calendar.MARCH && month <= Calendar.MAY) {
            return SeasonalDoodleView.SEASON_SPRING;
        }
        if (month >= Calendar.JUNE && month <= Calendar.AUGUST) {
            return SeasonalDoodleView.SEASON_SUMMER;
        }
        if (month >= Calendar.SEPTEMBER && month <= Calendar.NOVEMBER) {
            return SeasonalDoodleView.SEASON_AUTUMN;
        }
        return SeasonalDoodleView.SEASON_WINTER;
    }

    private void drawDoodleSeasonalParticles(Canvas canvas, Paint paint, int width, int height,
            int season) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 16; i++) {
            float x = width * (0.13f + (i * 0.061f) % 0.73f);
            float y = height * (0.18f + ((i * 37) % 62) / 100f);
            float r = width * (0.008f + (i % 3) * 0.003f);
            if (season == SeasonalDoodleView.SEASON_SPRING) {
                paint.setColor(i % 2 == 0
                        ? Color.argb(170, 255, 180, 210)
                        : Color.argb(160, 180, 245, 168));
                canvas.drawCircle(x, y, r, paint);
                canvas.drawCircle(x + r * 1.3f, y, r * 0.8f, paint);
            } else if (season == SeasonalDoodleView.SEASON_SUMMER) {
                paint.setColor(i % 2 == 0
                        ? Color.argb(178, 255, 214, 82)
                        : Color.argb(160, 255, 126, 78));
                drawSmallSpark(canvas, paint, x, y, r * 2.3f);
            } else if (season == SeasonalDoodleView.SEASON_AUTUMN) {
                paint.setColor(i % 2 == 0
                        ? Color.argb(180, 229, 132, 62)
                        : Color.argb(165, 244, 185, 77));
                Path leaf = new Path();
                leaf.moveTo(x, y - r * 1.8f);
                leaf.cubicTo(x + r * 2.1f, y - r * 0.5f, x + r * 1.2f,
                        y + r * 2.0f, x, y + r * 2.2f);
                leaf.cubicTo(x - r * 1.2f, y + r * 2.0f, x - r * 2.1f,
                        y - r * 0.5f, x, y - r * 1.8f);
                canvas.drawPath(leaf, paint);
            } else {
                paint.setColor(Color.argb(180, 235, 252, 255));
                paint.setStrokeWidth(dp(1));
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(x, y, r * 1.6f, paint);
                canvas.drawLine(x - r * 2.1f, y, x + r * 2.1f, y, paint);
                canvas.drawLine(x, y - r * 2.1f, x, y + r * 2.1f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    private void drawDoodleChargingMark(Canvas canvas, Paint paint, int width, int height) {
        float cx = width * 0.50f;
        float cy = height * 0.78f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(4));
        paint.setColor(Color.argb(200, 255, 255, 255));
        canvas.drawLine(cx - width * 0.07f, cy, cx + width * 0.07f, cy, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(230, 77, 215, 218));
        Path bolt = new Path();
        bolt.moveTo(cx - width * 0.012f, cy - height * 0.055f);
        bolt.lineTo(cx + width * 0.026f, cy - height * 0.006f);
        bolt.lineTo(cx + width * 0.004f, cy - height * 0.006f);
        bolt.lineTo(cx + width * 0.018f, cy + height * 0.052f);
        bolt.lineTo(cx - width * 0.030f, cy - height * 0.018f);
        bolt.lineTo(cx - width * 0.006f, cy - height * 0.018f);
        bolt.close();
        canvas.drawPath(bolt, paint);
    }

    private void drawBatteryGlyph(Canvas canvas, Paint paint, float left, float top,
            float width, float height) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(218, 255, 255, 255));
        canvas.drawRoundRect(left, top, left + width, top + height,
                dp(3), dp(3), paint);
        canvas.drawRoundRect(left + width + dp(2), top + height * 0.28f,
                left + width + dp(6), top + height * 0.72f, dp(2), dp(2), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(210, 95, 235, 150));
        canvas.drawRoundRect(left + dp(3), top + dp(3),
                left + width - dp(3), top + height - dp(3), dp(2), dp(2), paint);
    }

    private void drawSmallSpark(Canvas canvas, Paint paint, float cx, float cy, float radius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius * 0.28f, paint);
    }

    private File colormapScreenshotFileForPreview(int effect) {
        String profile = FoldDisplayTarget.cacheProfileForContext(this);
        return colormapScreenshotFileForPreview(effect, profile);
    }

    private File colormapScreenshotFileForPreview(int effect, String profile) {
        profile = FoldDisplayTarget.normalizeProfile(profile);
        File shared = OverlayPrefs.effectBackgroundFile(this, effect, profile);
        if (shared.exists() && shared.length() > 0L) {
            return shared;
        }
        if (!FoldDisplayTarget.PROFILE_SINGLE.equals(profile)) {
            // The service validates dimensions before migrating a legacy screenshot.
            // The settings preview must never copy a cover bitmap into the inner slot.
            return shared;
        }
        File legacy = latestLegacyColormapScreenshotFile();
        if (legacy != null && copyColormapScreenshotFile(legacy, shared)) {
            return shared;
        }
        return shared;
    }

    private File latestLegacyColormapScreenshotFile() {
        File best = null;
        int[] effects = {
                OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
        };
        for (int candidate : effects) {
            File file = OverlayPrefs.legacyEffectBackgroundFile(this, candidate);
            if (!file.exists() || file.length() <= 0L) {
                continue;
            }
            if (best == null || file.lastModified() > best.lastModified()) {
                best = file;
            }
        }
        return best;
    }

    private boolean copyColormapScreenshotFile(File source, File target) {
        if (source == null || target == null || !source.exists() || source.length() <= 0L) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        File temp = new File(parent, target.getName() + ".tmp");
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(temp);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            return temp.renameTo(target);
        } catch (Throwable t) {
            Log.d("LLEControl", "colormap screenshot migration failed", t);
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
            if (temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    private Bitmap decodePreviewBitmap(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private Bitmap decodeFoldPreviewBitmap(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 960) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private int previewSampleSize(int width, int height) {
        int maxWidth = Math.max(dp(320), getResources().getDisplayMetrics().widthPixels);
        int maxHeight = Math.max(dp(480), getResources().getDisplayMetrics().heightPixels);
        int sample = 1;
        while ((width / sample) > maxWidth * 2 || (height / sample) > maxHeight * 2) {
            sample *= 2;
        }
        return sample;
    }

    private View effectOption(String title, String subtitle, final int value, int current) {
        return effectOption(title, subtitle, value, value == current, -1);
    }

    private View abstractTilesEffectOption(String title, String subtitle,
            boolean lineEnabled, int current, boolean currentLineEnabled) {
        return effectOption(
                title,
                subtitle,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                current == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                        && lineEnabled == currentLineEnabled,
                lineEnabled ? 1 : 0);
    }

    private View effectOption(String title, String subtitle, final int value,
            final boolean selected, final int abstractTilesLineMode) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setMinimumHeight(dp(82));
        final boolean[] previewOpened = new boolean[] {false};
        final float[] previewDown = new float[] {0f, 0f};
        final Runnable previewRunnable = new Runnable() {
            @Override
            public void run() {
                if (tabSwipeDragging || tabAnimationRunning) {
                    return;
                }
                previewOpened[0] = true;
                row.setPressed(false);
                showEffectPreviewBubble(row, value, title, previewDown[0], previewDown[1]);
            }
        };
        row.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    previewOpened[0] = false;
                    previewDown[0] = event.getRawX();
                    previewDown[1] = event.getRawY();
                    uiHandler.removeCallbacks(previewRunnable);
                    uiHandler.postDelayed(previewRunnable, 430L);
                    return false;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    float dx = Math.abs(event.getRawX() - previewDown[0]);
                    float dy = Math.abs(event.getRawY() - previewDown[1]);
                    if (dx > dp(14) || dy > dp(14)) {
                        uiHandler.removeCallbacks(previewRunnable);
                    }
                    return false;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    uiHandler.removeCallbacks(previewRunnable);
                    hideEffectPreviewBubble();
                }
                return false;
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (previewOpened[0]) {
                    previewOpened[0] = false;
                    return;
                }
                if (abstractTilesLineMode >= 0) {
                    queueAbstractTilesSelection(abstractTilesLineMode == 1);
                } else {
                    queueUnlockEffectSelection(value);
                }
                showTab(selectedTab);
            }
        });

        row.setBackground(optionBackground(selected));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            row.setElevation(selected ? dp(4) : dp(1));
        }

        View marker = new GraceEffectIconView(value, selected);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        markerParams.setMargins(0, 0, dp(14), 0);
        row.addView(marker, markerParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(16f);
        titleView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        titleView.setSingleLine(false);
        copy.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(COLOR_MUTED);
        subtitleView.setTextSize(13f);
        subtitleView.setSingleLine(false);
        subtitleView.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(2), 0, 0);
        copy.addView(subtitleView, subtitleParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rowParams);
        return row;
    }

    private View seasonalEffectOption(final String title, String subtitle,
            final int value, int current) {
        final boolean selected = value == current;
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setMinimumHeight(dp(82));

        final boolean[] previewOpened = new boolean[] {false};
        final float[] previewDown = new float[] {0f, 0f};
        final Runnable previewRunnable = new Runnable() {
            @Override
            public void run() {
                if (tabSwipeDragging || tabAnimationRunning) {
                    return;
                }
                previewOpened[0] = true;
                row.setPressed(false);
                showSeasonPreviewBubble(row, value, title,
                        previewDown[0], previewDown[1]);
            }
        };
        row.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    previewOpened[0] = false;
                    previewDown[0] = event.getRawX();
                    previewDown[1] = event.getRawY();
                    uiHandler.removeCallbacks(previewRunnable);
                    uiHandler.postDelayed(previewRunnable, 430L);
                    return false;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    float dx = Math.abs(event.getRawX() - previewDown[0]);
                    float dy = Math.abs(event.getRawY() - previewDown[1]);
                    if (dx > dp(14) || dy > dp(14)) {
                        uiHandler.removeCallbacks(previewRunnable);
                    }
                    return false;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    uiHandler.removeCallbacks(previewRunnable);
                    hideEffectPreviewBubble();
                }
                return false;
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (previewOpened[0]) {
                    previewOpened[0] = false;
                    return;
                }
                prefs.edit().putInt(OverlayPrefs.SEASON_MODE, value).apply();
                showTab(selectedTab);
            }
        });

        row.setBackground(optionBackground(selected));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            row.setElevation(selected ? dp(4) : dp(1));
        }

        View marker = new GraceSeasonIconView(value, selected);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        markerParams.setMargins(0, 0, dp(14), 0);
        row.addView(marker, markerParams);

        LinearLayout copy = verticalGroup();
        row.addView(copy, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(16f);
        titleView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        copy.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(COLOR_MUTED);
        subtitleView.setTextSize(13f);
        subtitleView.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(2), 0, 0);
        copy.addView(subtitleView, subtitleParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rowParams);
        return row;
    }

    private void addEffectOption(RadioGroup group, String label, int value) {
        RadioButton button = new RadioButton(this);
        button.setText(label);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(17f);
        button.setTag(Integer.valueOf(value));
        button.setPadding(0, dp(5), 0, dp(5));
        group.addView(button, new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT,
                dp(44)));
    }

    private TextView infoText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(13f);
        view.setLineSpacing(dp(2), 1.0f);
        view.setPadding(0, dp(8), 0, dp(12));
        view.setBackground(infoBackground());
        return view;
    }

    private View positionControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(params);
        styleCard(section);

        final TextView header = new TextView(this);
        header.setText(doodlePositionExpanded
                ? "Size and position   ▾"
                : "Size and position   +");
        header.setTextColor(COLOR_ACCENT_DEEP);
        header.setTextSize(16f);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setIncludeFontPadding(false);
        header.setPadding(dp(14), 0, dp(14), 0);
        header.setBackground(controlRowBackground(false));
        section.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));

        final LinearLayout content = verticalGroup();
        content.setPadding(0, dp(6), 0, 0);
        content.addView(doodleSizeSlider());
        content.addView(positionSlider("Horizontal", OverlayPrefs.POSITION_OFFSET_X, 0));
        content.addView(positionSlider("Vertical", OverlayPrefs.POSITION_OFFSET_Y, 0));
        setRevealState(content, doodlePositionExpanded, false);
        section.addView(content);

        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doodlePositionExpanded = !doodlePositionExpanded;
                header.setText(doodlePositionExpanded
                        ? "Size and position   ▾"
                        : "Size and position   +");
                setRevealState(content, doodlePositionExpanded, true);
            }
        });
        return section;
    }

    private View doodleDebugControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 0);
        section.setLayoutParams(params);
        styleInsetPanel(section);
        section.addView(toggle("Rolling battery percent", OverlayPrefs.DEBUG_ROLLING_CHARGE, false));
        return section;
    }

    private View lockscreenTouchControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(sectionTitle("Touch box"));

        touchBoxSummary = new TextView(this);
        touchBoxSummary.setTextColor(COLOR_MUTED);
        touchBoxSummary.setTextSize(14f);
        touchBoxSummary.setLineSpacing(dp(2), 1.0f);
        touchBoxSummary.setIncludeFontPadding(false);
        touchBoxSummary.setPadding(0, dp(8), 0, dp(12));
        touchBoxSummary.setBackground(infoBackground());
        section.addView(touchBoxSummary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        updateTouchBoxSummary();

        section.addView(invertedToggle("Show touch box", OverlayPrefs.DEBUG_TOUCH_TRANSPARENT, true));
        section.addView(toggle("AOD standby touch box", OverlayPrefs.DEBUG_TOUCH_STANDBY, true));
        section.addView(outlineButton(OverlayPrefs.foldModeEnabled(this)
                ? "Dual touch box wizard"
                : "Touch box screenshot wizard", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ControlActivity.this, TouchBoxSetupActivity.class);
                intent.putExtra(TouchBoxSetupActivity.EXTRA_START_CAPTURE, true);
                startActivity(intent);
            }
        }));
        section.addView(outlineButton(OverlayPrefs.foldModeEnabled(this)
                ? "Reset active panel touch box"
                : "Reset touch box", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OverlayPrefs.clearTouchBox(ControlActivity.this);
                updateTouchBoxSummary();
            }
        }));
        return section;
    }

    private View foldPanelRoutingControls() {
        LinearLayout section = verticalGroup();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(sectionTitle("Fold panels"));
        section.addView(infoText("These switches are combined with the global controls. "
                + "Disabling a panel leaves its saved screenshot and touch areas intact. "
                + "Active panel: " + FoldDisplayTarget.cacheProfileForContext(this) + "."));
        section.addView(sectionLabel("Cover screen"));
        section.addView(toggle("Allow lockscreen effect on Cover",
                OverlayPrefs.FOLD_COVER_UNLOCK_EFFECT_ENABLED, true));
        section.addView(toggle("Allow charging doodle on Cover",
                OverlayPrefs.FOLD_COVER_DOODLE_ENABLED, true));
        section.addView(sectionLabel("Main screen"));
        section.addView(toggle("Allow lockscreen effect on Main",
                OverlayPrefs.FOLD_MAIN_UNLOCK_EFFECT_ENABLED, true));
        section.addView(toggle("Allow charging doodle on Main",
                OverlayPrefs.FOLD_MAIN_DOODLE_ENABLED, true));
        return section;
    }

    private View lockscreenDebugMenu() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(collapsibleHeader("Debug", lockscreenDebugExpanded,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        lockscreenDebugExpanded = !lockscreenDebugExpanded;
                        showTab(selectedTab);
                    }
        }));
        if (lockscreenDebugExpanded) {
            section.addView(toggle("FOLD MODE (dual panels)", OverlayPrefs.FOLD_MODE,
                    FoldDisplayTarget.isFoldDevice(this)));
            section.addView(screenshotServiceDebugControls());
            section.addView(effectProfilerControls());
            section.addView(rootDebugControls());
        }
        return section;
    }

    private View rootDebugControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        section.setLayoutParams(params);
        styleInsetPanel(section);
        section.addView(sectionTitle("Root debug"));

        section.addView(infoText(batteryOptimizationStatus()));
        section.addView(outlineButton("Request battery unrestricted", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestBatteryOptimizationExemption();
            }
        }));
        section.addView(outlineButton("Open Samsung battery settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatteryOptimizationSettings();
            }
        }));
        section.addView(toggle("Enable root debug tools", OverlayPrefs.ROOT_DEBUG_ENABLED, false));
        section.addView(toggle("Root touch capture test", OverlayPrefs.ROOT_TOUCH_CAPTURE_TEST_ENABLED, false));
        section.addView(toggle("Root keepalive plan", OverlayPrefs.ROOT_KEEPALIVE_PLAN_ENABLED, false));
        section.addView(outlineButton("Root: check su", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!requireRootDebugEnabled()) {
                    return;
                }
                runRootDebugAction("Checking root", new RootTask() {
                    @Override
                    public RootDebugTools.Result run() {
                        return RootDebugTools.checkRoot();
                    }
                });
            }
        }));
        section.addView(outlineButton("Root: benchmark touch 8s", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!requireRootFeature(OverlayPrefs.ROOT_TOUCH_CAPTURE_TEST_ENABLED,
                        "Enable root touch capture test first")) {
                    return;
                }
                runRootDebugAction("Capturing root touch events", new RootTask() {
                    @Override
                    public RootDebugTools.Result run() {
                        return RootDebugTools.captureTouchEvents(ControlActivity.this, 8000);
                    }
                });
            }
        }));
        section.addView(outlineButton("Root: write debug report", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!requireRootDebugEnabled()) {
                    return;
                }
                runRootDebugAction("Writing root debug report", new RootTask() {
                    @Override
                    public RootDebugTools.Result run() {
                        return RootDebugTools.writeDebugReport(ControlActivity.this);
                    }
                });
            }
        }));
        section.addView(outlineButton("Root: write keepalive plan", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!requireRootFeature(OverlayPrefs.ROOT_KEEPALIVE_PLAN_ENABLED,
                        "Enable root keepalive plan first")) {
                    return;
                }
                runRootDebugAction("Writing root keepalive plan", new RootTask() {
                    @Override
                    public RootDebugTools.Result run() {
                        return RootDebugTools.writeKeepAlivePlan(ControlActivity.this);
                    }
                });
            }
        }));
        section.addView(outlineButton("Root: apply keepalive", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!requireRootFeature(OverlayPrefs.ROOT_KEEPALIVE_PLAN_ENABLED,
                        "Enable root keepalive plan first")) {
                    return;
                }
                runRootDebugAction("Applying root keepalive", new RootTask() {
                    @Override
                    public RootDebugTools.Result run() {
                        return RootDebugTools.applyKeepAlivePlan(ControlActivity.this);
                    }
                });
            }
        }));
        section.addView(outlineButton("Root: revert keepalive", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!requireRootDebugEnabled()) {
                    return;
                }
                runRootDebugAction("Reverting root keepalive", new RootTask() {
                    @Override
                    public RootDebugTools.Result run() {
                        return RootDebugTools.revertKeepAlivePlan(ControlActivity.this);
                    }
                });
            }
        }));
        return section;
    }

    private String batteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "Battery optimization: not applicable on this Android version.";
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ignored = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        return ignored
                ? "Battery optimization: unrestricted for LLE."
                : "Battery optimization: LLE is not unrestricted. This can delay warm wake rendering.";
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            openBatteryOptimizationSettings();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (RuntimeException e) {
            openBatteryOptimizationSettings();
        }
    }

    private void openBatteryOptimizationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean requireRootDebugEnabled() {
        if (prefs.getBoolean(OverlayPrefs.ROOT_DEBUG_ENABLED, false)) {
            return true;
        }
        Toast.makeText(this, "Enable root debug tools first", Toast.LENGTH_SHORT).show();
        return false;
    }

    private boolean requireRootFeature(String key, String message) {
        if (!requireRootDebugEnabled()) {
            return false;
        }
        if (prefs.getBoolean(key, false)) {
            return true;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        return false;
    }

    private void runRootDebugAction(String runningMessage, final RootTask task) {
        Toast.makeText(this, runningMessage, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                RootDebugTools.Result result;
                try {
                    result = task.run();
                } catch (RuntimeException e) {
                    result = RootDebugTools.Result.error(
                            "Root action failed: " + e.getMessage(), null);
                }
                final RootDebugTools.Result finalResult = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String prefix = finalResult.success ? "Root OK: " : "Root failed: ";
                        Log.i("LleRootDebug", prefix + finalResult.message);
                        Toast.makeText(ControlActivity.this,
                                finalResult.message,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "LLE-root-debug").start();
    }

    private interface RootTask {
        RootDebugTools.Result run();
    }

    private void updateTouchBoxSummary() {
        if (touchBoxSummary == null) {
            return;
        }
        if (OverlayPrefs.foldModeEnabled(this)) {
            String activeProfile = FoldDisplayTarget.cacheProfileForContext(this);
            touchBoxSummary.setText(touchBoxProfileSummary("Cover",
                    FoldDisplayTarget.PROFILE_COVER)
                    + "\n" + touchBoxProfileSummary("Main",
                    FoldDisplayTarget.PROFILE_MAIN)
                    + "\nActive panel: " + activeProfile);
            return;
        }
        boolean configured = OverlayPrefs.touchBoxConfigured(this);
        int left = OverlayPrefs.touchBoxLeft(this);
        int top = OverlayPrefs.touchBoxTop(this);
        int right = OverlayPrefs.touchBoxRight(this);
        int bottom = OverlayPrefs.touchBoxBottom(this);
        ArrayList<Rect> areas = OverlayPrefs.touchBoxRegions(this);
        if (!areas.isEmpty()) {
            Rect bounds = new Rect(areas.get(0));
            for (int i = 1; i < areas.size(); i++) {
                bounds.union(areas.get(i));
            }
            left = bounds.left;
            top = bounds.top;
            right = bounds.right;
            bottom = bounds.bottom;
        }
        File screenshot = OverlayPrefs.touchBoxScreenshotFile(this);
        String cache = screenshot.exists() && screenshot.length() > 0L
                ? "screenshot cache ready"
                : "no screenshot cache";
        touchBoxSummary.setText((configured ? "Current" : "Default")
                + ": " + areas.size() + (areas.size() == 1 ? " area, " : " areas, ")
                + left + "," + top + " - " + right + "," + bottom
                + " (" + (right - left) + " x " + (bottom - top) + ")"
                + "\n" + cache);
    }

    private String touchBoxProfileSummary(String label, String profile) {
        boolean configured = OverlayPrefs.touchBoxConfigured(this, profile);
        ArrayList<Rect> areas = OverlayPrefs.touchBoxRegions(this, profile);
        File screenshot = OverlayPrefs.touchBoxScreenshotFile(this, profile);
        File effectScreenshot = OverlayPrefs.effectBackgroundFile(
                this, OverlayPrefs.unlockEffect(this), profile);
        String cache;
        if (screenshot.exists() && screenshot.length() > 0L) {
            cache = "wizard cache ready";
        } else if (effectScreenshot.exists() && effectScreenshot.length() > 0L) {
            cache = "effect screenshot reusable";
        } else {
            cache = "no cache";
        }
        return label + ": " + (configured ? "custom" : "default")
                + ", " + areas.size() + (areas.size() == 1 ? " area" : " areas")
                + ", " + cache;
    }

    private View positionSlider(final String label, final String key, int defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 0);
        row.setLayoutParams(rowParams);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setBackground(controlRowBackground(false));

        final TextView valueLabel = new TextView(this);
        valueLabel.setTextColor(COLOR_ACCENT_DEEP);
        valueLabel.setTextSize(15f);
        valueLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(valueLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar slider = new SeekBar(this);
        int range = OverlayPrefs.POSITION_OFFSET_MAX - OverlayPrefs.POSITION_OFFSET_MIN;
        int current = OverlayPrefs.clampPositionOffset(prefs.getInt(key, defaultValue));
        slider.setMax(range);
        slider.setProgress(current - OverlayPrefs.POSITION_OFFSET_MIN);
        tintSeekBar(slider);
        updatePositionLabel(valueLabel, label, current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = OverlayPrefs.POSITION_OFFSET_MIN + progress;
                updatePositionLabel(valueLabel, label, value);
                if (fromUser) {
                    prefs.edit().putInt(key, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int value = OverlayPrefs.POSITION_OFFSET_MIN + seekBar.getProgress();
                prefs.edit().putInt(key, value).apply();
            }
        });
        row.addView(slider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)));
        return row;
    }

    private View doodleSizeSlider() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 0);
        row.setLayoutParams(rowParams);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setBackground(controlRowBackground(false));

        final TextView valueLabel = new TextView(this);
        valueLabel.setTextColor(COLOR_ACCENT_DEEP);
        valueLabel.setTextSize(15f);
        valueLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(valueLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar slider = new SeekBar(this);
        int min = OverlayPrefs.DOODLE_SIZE_MIN_PERCENT;
        int max = OverlayPrefs.DOODLE_SIZE_MAX_PERCENT;
        int current = OverlayPrefs.doodleSizePercent(this);
        slider.setMax(max - min);
        slider.setProgress(current - min);
        tintSeekBar(slider);
        updateDoodleSizeLabel(valueLabel, current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = OverlayPrefs.DOODLE_SIZE_MIN_PERCENT + progress;
                updateDoodleSizeLabel(valueLabel, value);
                if (fromUser) {
                    prefs.edit().putInt(OverlayPrefs.DOODLE_SIZE_PERCENT, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int value = OverlayPrefs.DOODLE_SIZE_MIN_PERCENT + seekBar.getProgress();
                prefs.edit().putInt(OverlayPrefs.DOODLE_SIZE_PERCENT, value).apply();
            }
        });
        row.addView(slider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)));
        return row;
    }

    private void updateDoodleSizeLabel(TextView valueLabel, int value) {
        valueLabel.setText("Size: " + value + "%");
    }

    private void updatePositionLabel(TextView valueLabel, String label, int value) {
        valueLabel.setText(label + ": " + signedValue(value));
    }

    private String signedValue(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private int clampInt(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private void ensureTouchAreaEnabled() {
        if (!prefs.getBoolean(OverlayPrefs.DEBUG_TOUCH_AREA, true)) {
            prefs.edit().putBoolean(OverlayPrefs.DEBUG_TOUCH_AREA, true).apply();
        }
    }

    private Button button(String text, View.OnClickListener listener) {
        return outlineButton(text, listener);
    }

    private Button outlineButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setTextColor(COLOR_ACCENT_DEEP);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setOnClickListener(listener);
        button.setBackground(buttonBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(0f);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        params.setMargins(0, dp(4), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void updateAccessibilityStatus() {
        if (accessibilityStatus == null || serviceSwitch == null) {
            return;
        }
        boolean accessibilityEnabled = isChargingAccessibilityEnabled();
        boolean masterEnabled = OverlayPrefs.masterEnabled(this);
        updatingServiceSwitch = true;
        serviceSwitch.setChecked(accessibilityEnabled && masterEnabled);
        serviceSwitch.setEnabled(true);
        serviceSwitch.setText("");
        updatingServiceSwitch = false;
        if (accessibilityEnabled) {
            accessibilityStatus.setText("\u2713");
            accessibilityStatus.setTextColor(COLOR_OK);
            accessibilityStatus.setBackground(statusBadgeBackground(
                    Color.argb(185, 22, 160, 98), Color.argb(42, 255, 255, 255)));
            accessibilityStatus.setContentDescription("Accessibility enabled. Tap to open settings.");
        } else {
            accessibilityStatus.setText("\u00d7");
            accessibilityStatus.setTextColor(COLOR_ERROR);
            accessibilityStatus.setBackground(statusBadgeBackground(
                    Color.argb(185, 207, 67, 72), Color.argb(42, 255, 255, 255)));
            accessibilityStatus.setContentDescription("Accessibility disabled. Tap to enable.");
        }
    }

    private boolean isChargingAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            return false;
        }
        String target = new ComponentName(this, ChargingAccessibilityService.class).flattenToString();
        String[] services = enabledServices.split(":");
        for (int i = 0; i < services.length; i++) {
            if (target.equals(services[i])) {
                return true;
            }
        }
        return false;
    }

    private GradientDrawable pageBackground() {
        return gradient(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.rgb(235, 245, 249), Color.rgb(224, 238, 244)},
                0,
                Color.TRANSPARENT,
                0);
    }

    private GradientDrawable headerBackground() {
        return gradient(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        Color.rgb(165, 236, 228),
                        Color.rgb(221, 197, 249),
                        Color.rgb(255, 216, 136)
                },
                0,
                Color.TRANSPARENT,
                0);
    }

    private GradientDrawable cardBackground(boolean accent) {
        return gradient(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.argb(252, 255, 255, 255), Color.rgb(249, 252, 253)},
                dp(22),
                accent ? Color.rgb(198, 224, 226) : Color.argb(105, 170, 192, 204),
                dp(1));
    }

    private GradientDrawable insetPanelBackground() {
        return solidDrawable(Color.TRANSPARENT, 0, Color.TRANSPARENT, 0);
    }

    private GradientDrawable infoBackground() {
        return solidDrawable(Color.TRANSPARENT, 0, Color.TRANSPARENT, 0);
    }

    private StateListDrawable buttonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed},
                solidDrawable(COLOR_ACCENT_SOFT, 0, Color.TRANSPARENT, 0));
        states.addState(new int[] {},
                solidDrawable(Color.TRANSPARENT, 0, Color.TRANSPARENT, 0));
        return states;
    }

    private StateListDrawable controlRowBackground(boolean selected) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed},
                solidDrawable(Color.rgb(225, 244, 244), dp(16),
                        Color.argb(80, 80, 170, 175), dp(1)));
        states.addState(new int[] {},
                solidDrawable(selected ? COLOR_ACCENT_SOFT : Color.rgb(248, 251, 252),
                        dp(16),
                        Color.argb(75, 174, 195, 205),
                        dp(1)));
        return states;
    }

    private StateListDrawable optionBackground(boolean selected) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed},
                gradient(GradientDrawable.Orientation.TL_BR,
                        new int[] {Color.rgb(225, 244, 244), Color.WHITE},
                        dp(18), Color.argb(100, 63, 164, 171), dp(1)));
        states.addState(new int[] {},
                selected
                        ? gradient(GradientDrawable.Orientation.TL_BR,
                                new int[] {Color.WHITE, Color.rgb(224, 247, 246)},
                                dp(18), Color.argb(150, 33, 158, 166), dp(1))
                        : solidDrawable(Color.WHITE, dp(18),
                                Color.argb(65, 167, 190, 201), dp(1)));
        return states;
    }

    private int effectAccentColor(int effect) {
        switch (effect) {
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                return Color.rgb(255, 194, 78);
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
                return Color.rgb(66, 169, 232);
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                return Color.rgb(123, 206, 92);
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                return Color.rgb(125, 113, 230);
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
                return Color.rgb(235, 111, 102);
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
                return Color.rgb(94, 210, 209);
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return Color.rgb(239, 157, 64);
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return Color.rgb(174, 111, 204);
            default:
                return COLOR_ACCENT;
        }
    }

    private GradientDrawable statusBadgeBackground(int strokeColor, int fillColor) {
        return solidDrawable(fillColor, dp(18), strokeColor, dp(1));
    }

    private GradientDrawable gradient(GradientDrawable.Orientation orientation, int[] colors,
            int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private GradientDrawable solidDrawable(int color, int radius, int strokeColor,
            int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private int blendColor(int first, int second, float secondWeight) {
        float amount = Math.max(0f, Math.min(1f, secondWeight));
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(first) * inverse + Color.red(second) * amount),
                Math.round(Color.green(first) * inverse + Color.green(second) * amount),
                Math.round(Color.blue(first) * inverse + Color.blue(second) * amount));
    }

    private ColorStateList accentTint() {
        return new ColorStateList(
                new int[][] {
                        new int[] {android.R.attr.state_checked},
                        new int[] {}
                },
                new int[] {
                        COLOR_ACCENT,
                        Color.rgb(143, 159, 181)
                });
    }

    private ColorStateList switchTrackTint() {
        return new ColorStateList(
                new int[][] {
                        new int[] {android.R.attr.state_checked},
                        new int[] {}
                },
                new int[] {
                        Color.argb(120, 44, 205, 213),
                        Color.rgb(198, 208, 221)
                });
    }

    private void tintSwitch(Switch toggle) {
        if (toggle == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        toggle.setThumbTintList(accentTint());
        toggle.setTrackTintList(switchTrackTint());
    }

    private void styleHeaderSwitch(Switch toggle) {
        if (toggle == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            tintSwitch(toggle);
            return;
        }
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[] {android.R.attr.state_checked},
                headerSwitchTrack(Color.argb(76, 0, 158, 166),
                        Color.argb(155, 0, 132, 142)));
        track.addState(new int[] {},
                headerSwitchTrack(Color.argb(28, 255, 255, 255),
                        Color.argb(120, 69, 88, 112)));

        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[] {android.R.attr.state_checked},
                headerSwitchThumb(Color.argb(238, 255, 255, 255), COLOR_ACCENT_DEEP));
        thumb.addState(new int[] {},
                headerSwitchThumb(Color.argb(205, 255, 255, 255),
                        Color.argb(150, 69, 88, 112)));

        toggle.setTrackDrawable(track);
        toggle.setThumbDrawable(thumb);
        toggle.setSplitTrack(false);
        toggle.setSwitchMinWidth(dp(50));
    }

    private GradientDrawable headerSwitchTrack(int fillColor, int strokeColor) {
        GradientDrawable drawable = solidDrawable(fillColor, dp(14), strokeColor, dp(1));
        drawable.setSize(dp(48), dp(26));
        return drawable;
    }

    private GradientDrawable headerSwitchThumb(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        drawable.setStroke(dp(1), strokeColor);
        drawable.setSize(dp(24), dp(24));
        return drawable;
    }

    private void tintRadio(RadioButton button) {
        if (button == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        button.setButtonTintList(accentTint());
    }

    private void tintSeekBar(SeekBar slider) {
        if (slider == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        slider.setProgressTintList(new ColorStateList(
                new int[][] {new int[] {}},
                new int[] {COLOR_ACCENT}));
        slider.setThumbTintList(new ColorStateList(
                new int[][] {new int[] {}},
                new int[] {COLOR_ACCENT_DEEP}));
        slider.setProgressBackgroundTintList(new ColorStateList(
                new int[][] {new int[] {}},
                new int[] {Color.rgb(198, 211, 227)}));
    }

    private final class GraceBackdropView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();

        GraceBackdropView() {
            super(ControlActivity.this);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            paint.setShader(new LinearGradient(0f, 0f, 0f, height,
                    new int[] {
                            Color.rgb(228, 241, 247),
                            Color.rgb(242, 248, 250),
                            Color.rgb(222, 237, 243)
                    },
                    new float[] {0f, 0.46f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, width, height, paint);
            paint.setShader(null);

            shape.reset();
            shape.moveTo(width * 0.62f, 0f);
            shape.lineTo(width, 0f);
            shape.lineTo(width, height * 0.28f);
            shape.lineTo(width * 0.84f, height * 0.20f);
            shape.close();
            paint.setColor(Color.argb(58, 111, 82, 214));
            canvas.drawPath(shape, paint);

            shape.reset();
            shape.moveTo(0f, height * 0.60f);
            shape.lineTo(width * 0.34f, height * 0.50f);
            shape.lineTo(width * 0.53f, height);
            shape.lineTo(0f, height);
            shape.close();
            paint.setColor(Color.argb(52, 54, 203, 158));
            canvas.drawPath(shape, paint);

            shape.reset();
            shape.moveTo(0f, height * 0.23f);
            shape.lineTo(width * 0.22f, height * 0.18f);
            shape.lineTo(width * 0.40f, height * 0.42f);
            shape.lineTo(0f, height * 0.48f);
            shape.close();
            paint.setColor(Color.argb(42, 255, 176, 54));
            canvas.drawPath(shape, paint);

            paint.setShader(new RadialGradient(width * 0.18f, height * 0.72f,
                    width * 0.32f,
                    new int[] {Color.argb(48, 255, 92, 166), Color.TRANSPARENT},
                    null,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(width * 0.18f, height * 0.72f, width * 0.32f, paint);
            paint.setShader(null);

            paint.setShader(new RadialGradient(width * 0.82f, height * 0.42f,
                    width * 0.52f,
                    new int[] {Color.argb(55, 67, 191, 204), Color.TRANSPARENT},
                    null,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(width * 0.82f, height * 0.42f, width * 0.52f, paint);
            paint.setShader(null);
        }
    }

    private int seasonAccentColor(int season) {
        switch (season) {
            case SeasonalDoodleView.SEASON_SPRING:
                return Color.rgb(237, 136, 185);
            case SeasonalDoodleView.SEASON_SUMMER:
                return Color.rgb(255, 184, 58);
            case SeasonalDoodleView.SEASON_AUTUMN:
                return Color.rgb(222, 119, 55);
            case SeasonalDoodleView.SEASON_WINTER:
                return Color.rgb(98, 177, 220);
            default:
                return Color.rgb(91, 199, 194);
        }
    }

    private final class GraceSeasonIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int season;
        private final boolean selected;

        GraceSeasonIconView(int season, boolean selected) {
            super(ControlActivity.this);
            this.season = season;
            this.selected = selected;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(1.5f);
            RectF bounds = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            float radius = Math.min(getWidth(), getHeight()) * 0.27f;
            int accent = seasonAccentColor(season);
            int deep = blendColor(COLOR_GRACE_NAVY, accent, 0.58f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    new int[] {blendColor(Color.WHITE, accent, 0.18f), accent, deep},
                    new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setShader(null);

            float cx = bounds.centerX() - dp(2);
            float cy = bounds.centerY() - dp(2);
            float unit = Math.min(bounds.width(), bounds.height());
            paint.setColor(Color.argb(235, 255, 255, 255));
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (season == SeasonalDoodleView.SEASON_AUTO) {
                int[] colors = {
                        Color.rgb(255, 176, 207), Color.rgb(255, 213, 83),
                        Color.rgb(229, 132, 61), Color.rgb(190, 232, 255)
                };
                for (int i = 0; i < colors.length; i++) {
                    double angle = -Math.PI * 0.5 + i * Math.PI * 0.5;
                    paint.setColor(colors[i]);
                    canvas.drawCircle(cx + (float) Math.cos(angle) * unit * 0.18f,
                            cy + (float) Math.sin(angle) * unit * 0.18f,
                            unit * 0.105f, paint);
                }
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx, cy, unit * 0.10f, paint);
            } else if (season == SeasonalDoodleView.SEASON_SPRING) {
                for (int i = 0; i < 5; i++) {
                    double angle = -Math.PI * 0.5 + i * Math.PI * 0.4;
                    canvas.drawCircle(cx + (float) Math.cos(angle) * unit * 0.16f,
                            cy + (float) Math.sin(angle) * unit * 0.16f,
                            unit * 0.12f, paint);
                }
                paint.setColor(Color.rgb(255, 218, 89));
                canvas.drawCircle(cx, cy, unit * 0.09f, paint);
            } else if (season == SeasonalDoodleView.SEASON_SUMMER) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(unit * 0.055f);
                canvas.drawCircle(cx, cy, unit * 0.15f, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI * 0.25;
                    canvas.drawLine(cx + (float) Math.cos(angle) * unit * 0.22f,
                            cy + (float) Math.sin(angle) * unit * 0.22f,
                            cx + (float) Math.cos(angle) * unit * 0.31f,
                            cy + (float) Math.sin(angle) * unit * 0.31f, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            } else if (season == SeasonalDoodleView.SEASON_AUTUMN) {
                Path leaf = new Path();
                leaf.moveTo(cx, cy - unit * 0.27f);
                leaf.cubicTo(cx + unit * 0.30f, cy - unit * 0.12f,
                        cx + unit * 0.22f, cy + unit * 0.24f, cx, cy + unit * 0.29f);
                leaf.cubicTo(cx - unit * 0.22f, cy + unit * 0.16f,
                        cx - unit * 0.28f, cy - unit * 0.10f, cx, cy - unit * 0.27f);
                canvas.drawPath(leaf, paint);
                paint.setColor(Color.argb(150, 136, 66, 36));
                paint.setStrokeWidth(unit * 0.035f);
                canvas.drawLine(cx - unit * 0.12f, cy + unit * 0.18f,
                        cx + unit * 0.12f, cy - unit * 0.15f, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(unit * 0.055f);
                for (int i = 0; i < 3; i++) {
                    double angle = i * Math.PI / 3.0;
                    float dx = (float) Math.cos(angle) * unit * 0.27f;
                    float dy = (float) Math.sin(angle) * unit * 0.27f;
                    canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }

            // Small magnifier: this icon is also the long-press preview affordance.
            float previewCx = bounds.right - unit * 0.16f;
            float previewCy = bounds.bottom - unit * 0.17f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(205, 35, 61, 91));
            canvas.drawCircle(previewCx, previewCy, unit * 0.125f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(dp(1.2f), unit * 0.025f));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(previewCx - unit * 0.018f, previewCy - unit * 0.018f,
                    unit * 0.050f, paint);
            canvas.drawLine(previewCx + unit * 0.020f, previewCy + unit * 0.020f,
                    previewCx + unit * 0.075f, previewCy + unit * 0.075f, paint);

            paint.setStrokeWidth(dp(selected ? 2f : 1f));
            paint.setColor(selected ? Color.WHITE : Color.argb(105, 255, 255, 255));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private final class GraceEffectIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int effect;
        private final boolean selected;
        private final Drawable iconDrawable;

        GraceEffectIconView(int effect, boolean selected) {
            super(ControlActivity.this);
            this.effect = effect;
            this.selected = selected;
            int iconResId = effectIconResId(effect);
            this.iconDrawable = iconResId == 0
                    ? null
                    : getResources().getDrawable(iconResId);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(1.5f);
            RectF bounds = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            if (iconDrawable != null) {
                iconDrawable.setBounds(
                        Math.round(bounds.left),
                        Math.round(bounds.top),
                        Math.round(bounds.right),
                        Math.round(bounds.bottom));
                iconDrawable.draw(canvas);
                return;
            }
            float radius = Math.min(getWidth(), getHeight()) * 0.27f;
            int accent = effectAccentColor(effect);
            int deep = blendColor(COLOR_GRACE_NAVY, accent, 0.54f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    new int[] {blendColor(Color.WHITE, accent, 0.22f), accent, deep},
                    new float[] {0f, 0.52f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setShader(null);

            paint.setColor(Color.argb(62, 255, 255, 255));
            canvas.drawRoundRect(new RectF(bounds.left + dp(3), bounds.top + dp(3),
                    bounds.right - dp(3), bounds.centerY()), radius * 0.78f, radius * 0.78f, paint);
            drawEffectMotif(canvas, paint, effect,
                    new RectF(bounds.left + dp(10), bounds.top + dp(10),
                            bounds.right - dp(10), bounds.bottom - dp(10)),
                    Color.WHITE,
                    selected ? 1f : 0.90f);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(selected ? 2f : 1f));
            paint.setColor(selected ? Color.WHITE : Color.argb(105, 255, 255, 255));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawEffectMotif(Canvas canvas, Paint paint, int effect, RectF rect,
            int color, float alpha) {
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round(255f * alpha)));
        paint.setShader(null);
        paint.setColor(Color.argb(resolvedAlpha,
                Color.red(color), Color.green(color), Color.blue(color)));
        paint.setStrokeCap(Paint.Cap.ROUND);
        float cx = rect.centerX();
        float cy = rect.centerY();
        float unit = Math.min(rect.width(), rect.height());

        switch (effect) {
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.075f));
                canvas.drawCircle(cx, cy, unit * 0.24f, paint);
                canvas.drawLine(rect.left, cy, rect.right, cy, paint);
                canvas.drawLine(cx, rect.top, cx, rect.bottom, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, unit * 0.095f, paint);
                break;
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - unit * 0.18f, cy + unit * 0.12f, unit * 0.19f, paint);
                canvas.drawCircle(cx + unit * 0.17f, cy - unit * 0.15f, unit * 0.16f, paint);
                canvas.drawCircle(cx + unit * 0.19f, cy + unit * 0.23f, unit * 0.10f, paint);
                break;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.075f));
                canvas.drawRect(rect.left + unit * 0.04f, rect.top + unit * 0.06f,
                        cx - unit * 0.03f, cy - unit * 0.02f, paint);
                canvas.drawRect(cx + unit * 0.04f, cy + unit * 0.02f,
                        rect.right - unit * 0.02f, rect.bottom - unit * 0.06f, paint);
                canvas.drawLine(cx + unit * 0.06f, rect.top + unit * 0.04f,
                        rect.right - unit * 0.02f, cy - unit * 0.08f, paint);
                break;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.07f));
                Path mosaic = new Path();
                mosaic.moveTo(cx, rect.top);
                mosaic.lineTo(rect.right, cy);
                mosaic.lineTo(cx, rect.bottom);
                mosaic.lineTo(rect.left, cy);
                mosaic.close();
                mosaic.moveTo(cx, rect.top);
                mosaic.lineTo(cx, rect.bottom);
                mosaic.moveTo(rect.left, cy);
                mosaic.lineTo(rect.right, cy);
                canvas.drawPath(mosaic, paint);
                break;
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.065f));
                canvas.drawCircle(cx - unit * 0.16f, cy + unit * 0.12f, unit * 0.20f, paint);
                canvas.drawCircle(cx + unit * 0.18f, cy - unit * 0.14f, unit * 0.14f, paint);
                canvas.drawCircle(cx + unit * 0.23f, cy + unit * 0.25f, unit * 0.07f, paint);
                break;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
                paint.setStyle(Paint.Style.FILL);
                Path drop = new Path();
                drop.moveTo(cx, rect.top);
                drop.cubicTo(cx + unit * 0.08f, cy - unit * 0.18f,
                        rect.right - unit * 0.08f, cy + unit * 0.02f, cx, rect.bottom);
                drop.cubicTo(rect.left + unit * 0.08f, cy + unit * 0.02f,
                        cx - unit * 0.08f, cy - unit * 0.18f, cx, rect.top);
                drop.close();
                canvas.drawPath(drop, paint);
                break;
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.065f));
                canvas.drawOval(new RectF(cx - unit * 0.42f, cy - unit * 0.18f,
                        cx + unit * 0.42f, cy + unit * 0.18f), paint);
                canvas.drawOval(new RectF(cx - unit * 0.25f, cy - unit * 0.10f,
                        cx + unit * 0.25f, cy + unit * 0.10f), paint);
                break;
            default:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(2), unit * 0.12f));
                canvas.drawArc(new RectF(rect.left, cy - unit * 0.25f,
                        rect.right, cy + unit * 0.25f), 200f, 235f, false, paint);
                break;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void forceSansSerif(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Typeface current = textView.getTypeface();
            int style = current == null ? Typeface.NORMAL : current.getStyle();
            textView.setTypeface(appTypeface(style));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                forceSansSerif(group.getChildAt(i));
            }
        }
    }

    private Typeface appTypeface(int style) {
        if ((style & Typeface.BOLD) != 0) {
            if (appFontBold == null) {
                appFontBold = loadPreferredTypeface(Typeface.BOLD);
            }
            return appFontBold;
        }
        if (appFontRegular == null) {
            appFontRegular = loadPreferredTypeface(Typeface.NORMAL);
        }
        return appFontRegular;
    }

    private Typeface loadPreferredTypeface(int fallbackStyle) {
        try {
            String path = fallbackStyle == Typeface.BOLD
                    ? "/system/fonts/SourceSansPro-SemiBold.ttf"
                    : "/system/fonts/SourceSansPro-Regular.ttf";
            Typeface samsungEraSans = Typeface.createFromFile(path);
            if (samsungEraSans != null) {
                return samsungEraSans;
            }
        } catch (RuntimeException ignored) {
            // Fall back to the platform family on ROMs without Samsung's system font set.
        }
        String family = fallbackStyle == Typeface.BOLD ? "sans-serif-medium" : "sans-serif";
        Typeface typeface = Typeface.create(family, fallbackStyle);
        return typeface == null ? Typeface.defaultFromStyle(fallbackStyle) : typeface;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            return getResources().getDimensionPixelSize(id);
        }
        return dp(24);
    }
}

