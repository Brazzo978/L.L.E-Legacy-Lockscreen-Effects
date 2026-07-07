package com.codex.lle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class ControlActivity extends Activity {
    private static final String STATE_SELECTED_TAB = "selected_tab";
    private static final int TAB_CHARGING_DOODLE = 0;
    private static final int TAB_LOCKSCREEN_EFFECT = 1;
    private static final int COLOR_BACKGROUND = Color.rgb(244, 246, 249);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(33, 40, 48);
    private static final int COLOR_MUTED = Color.rgb(102, 114, 128);
    private static final int COLOR_DIVIDER = Color.rgb(214, 222, 232);
    private static final int COLOR_ACCENT = Color.rgb(24, 124, 235);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(228, 240, 255);
    private static final int COLOR_WARNING = Color.rgb(202, 126, 38);
    private static final int COLOR_OK = Color.rgb(24, 150, 82);
    private static final int COLOR_ERROR = Color.rgb(210, 70, 70);
    private static final int TAB_SWIPE_MIN_DISTANCE_DP = 72;
    private static final long TAB_ANIMATION_DURATION_MS = 180L;
    private static final float TAB_SWIPE_AXIS_RATIO = 1.35f;
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
    private LinearLayout tabContent;
    private TextView touchBoxSummary;
    private TextView effectProfilerSummary;
    private int selectedTab = TAB_CHARGING_DOODLE;
    private int pendingUnlockEffect = -1;
    private boolean updatingServiceSwitch;
    private float tabSwipeDownX;
    private float tabSwipeDownY;
    private boolean tabSwipeStartedOnSlider;
    private boolean tabAnimationRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureGraceWindow();
        prefs = OverlayPrefs.get(this);
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this);
        ensureTouchAreaEnabled();
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_SELECTED_TAB, TAB_CHARGING_DOODLE);
        }

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(COLOR_BACKGROUND);
        outer.setPadding(0, statusBarHeight(), 0, 0);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        outer.addView(appHeader(), headerParams);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        root.setBackgroundColor(COLOR_BACKGROUND);

        root.addView(tabSelector());

        tabContent = new LinearLayout(this);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        root.addView(tabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        showTab(selectedTab, false, 0);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        outer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        setContentView(outer);
        updateAccessibilityStatus();
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
            window.setStatusBarColor(COLOR_BACKGROUND);
            window.setNavigationBarColor(COLOR_SURFACE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private View appHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(8), dp(16), dp(8));
        header.setBackgroundColor(COLOR_SURFACE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            header.setElevation(dp(3));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("L.L.E");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(20f);
        title.setSingleLine(true);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        serviceSwitch = new Switch(this);
        serviceSwitch.setText("");
        serviceSwitch.setTextSize(13f);
        serviceSwitch.setTextColor(COLOR_TEXT);
        serviceSwitch.setMinWidth(dp(52));
        serviceSwitch.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            serviceSwitch.setShowText(false);
        }
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
                dp(62),
                dp(48)));

        accessibilityStatus = new TextView(this);
        accessibilityStatus.setGravity(Gravity.CENTER);
        accessibilityStatus.setTextSize(22f);
        accessibilityStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        accessibilityStatus.setClickable(true);
        accessibilityStatus.setFocusable(true);
        accessibilityStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAccessibilitySettings();
            }
        });
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                dp(30),
                dp(44));
        statusParams.setMargins(dp(4), 0, 0, 0);
        row.addView(accessibilityStatus, statusParams);
        return header;
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private View tabSelector() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(Color.rgb(232, 237, 244));
        tabs.setBackground(background);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        tabsParams.setMargins(0, 0, 0, dp(14));
        tabs.setLayoutParams(tabsParams);

        chargingDoodleTabButton = tabButton("Charging doodle", TAB_CHARGING_DOODLE);
        lockscreenEffectTabButton = tabButton("Lockscreen effect", TAB_LOCKSCREEN_EFFECT);

        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f);
        tabs.addView(chargingDoodleTabButton, firstParams);

        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f);
        secondParams.setMargins(dp(8), 0, 0, 0);
        tabs.addView(lockscreenEffectTabButton, secondParams);
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
                && tabContent.getWidth() > 0;

        selectedTab = targetTab;
        updateTabStyles();

        if (!canAnimate) {
            tabContent.animate().cancel();
            populateTabContent(targetTab);
            tabContent.setAlpha(1f);
            tabContent.setTranslationX(0f);
            return;
        }
        animateTabContentChange(targetTab, direction);
    }

    private int normalizeTab(int tab) {
        return tab == TAB_LOCKSCREEN_EFFECT ? TAB_LOCKSCREEN_EFFECT : TAB_CHARGING_DOODLE;
    }

    private void populateTabContent(int tab) {
        tabContent.removeAllViews();
        if (tab == TAB_CHARGING_DOODLE) {
            tabContent.addView(chargingDoodleControls());
        } else {
            tabContent.addView(effectSelector());
            tabContent.addView(effectProfilerControls());
            tabContent.addView(lockscreenTouchControls());
            tabContent.addView(rootDebugControls());
        }
    }

    private void animateTabContentChange(final int targetTab, final int direction) {
        tabAnimationRunning = true;
        final float width = Math.max(dp(120), tabContent.getWidth());
        tabContent.animate()
                .cancel();
        tabContent.animate()
                .alpha(0f)
                .translationX(-direction * width * 0.28f)
                .setDuration(TAB_ANIMATION_DURATION_MS / 2L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        populateTabContent(targetTab);
                        tabContent.setTranslationX(direction * width * 0.42f);
                        tabContent.setAlpha(0f);
                        tabContent.animate()
                                .alpha(1f)
                                .translationX(0f)
                                .setDuration(TAB_ANIMATION_DURATION_MS)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        tabContent.setAlpha(1f);
                                        tabContent.setTranslationX(0f);
                                        tabAnimationRunning = false;
                                    }

                                    @Override
                                    public void onAnimationCancel(Animator animation) {
                                        tabAnimationRunning = false;
                                    }
                                })
                                .start();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        tabAnimationRunning = false;
                    }
                })
                .start();
    }

    private boolean trackTabSwipe(MotionEvent event) {
        if (event == null) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            tabSwipeDownX = event.getRawX();
            tabSwipeDownY = event.getRawY();
            tabSwipeStartedOnSlider = isPointInsideViewType(
                    getWindow().getDecorView(),
                    tabSwipeDownX,
                    tabSwipeDownY,
                    SeekBar.class);
            return false;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean handled = maybeSwitchTabBySwipe(event);
            tabSwipeStartedOnSlider = false;
            return handled;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            tabSwipeStartedOnSlider = false;
        }
        return false;
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
        if (dx < 0f && selectedTab == TAB_CHARGING_DOODLE) {
            showTab(TAB_LOCKSCREEN_EFFECT, true, 1);
            return true;
        }
        if (dx > 0f && selectedTab == TAB_LOCKSCREEN_EFFECT) {
            showTab(TAB_CHARGING_DOODLE, true, -1);
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
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(9));
        background.setColor(selected ? COLOR_SURFACE : Color.TRANSPARENT);
        background.setStroke(dp(1),
                selected ? COLOR_DIVIDER : Color.TRANSPARENT);
        button.setBackground(background);
        button.setTextColor(selected ? COLOR_ACCENT : COLOR_MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void styleCard(LinearLayout section) {
        section.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        background.setColor(COLOR_SURFACE);
        background.setStroke(dp(1), Color.rgb(232, 237, 244));
        section.setBackground(background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            section.setElevation(dp(1));
        }
    }

    private void styleProfilerCard(LinearLayout section) {
        section.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        Color.rgb(255, 255, 255),
                        Color.rgb(232, 244, 255),
                        Color.rgb(250, 252, 255)
                });
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), Color.rgb(202, 226, 246));
        section.setBackground(background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            section.setElevation(dp(2));
        }
    }

    private TextView sectionTitle(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(18f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, 0, 0, dp(8));
        return label;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_MUTED);
        label.setTextSize(12f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(10), 0, dp(2));
        return label;
    }

    private Switch toggle(String label, final String key, boolean defaultValue) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(COLOR_TEXT);
        toggle.setTextSize(16f);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setPadding(0, dp(8), 0, dp(8));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, isChecked).apply();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56));
        params.setMargins(0, dp(4), 0, dp(4));
        toggle.setLayoutParams(params);
        return toggle;
    }

    private Switch invertedToggle(String label, final String key, boolean defaultStoredValue) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(COLOR_TEXT);
        toggle.setTextSize(16f);
        toggle.setChecked(!prefs.getBoolean(key, defaultStoredValue));
        toggle.setPadding(0, dp(8), 0, dp(8));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, !isChecked).apply();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56));
        params.setMargins(0, dp(4), 0, dp(4));
        toggle.setLayoutParams(params);
        return toggle;
    }

    private View chargingDoodleControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(sectionParams);
        styleCard(section);
        section.addView(sectionTitle("Charging doodle"));

        section.addView(toggle("Enable charging doodle", OverlayPrefs.SHOW_DOODLE, true));
        section.addView(toggle("Doodle on lockscreen", OverlayPrefs.SHOW_LOCK, true));
        section.addView(seasonSelector());
        section.addView(positionControls());
        section.addView(doodleDebugControls());
        return section;
    }

    private View seasonSelector() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(0, dp(10), 0, dp(12));
        section.setLayoutParams(sectionParams);
        styleCard(section);
        section.addView(sectionTitle("Seasonal doodle"));

        final RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        addSeasonOption(group, "Seasonal auto", SeasonalDoodleView.SEASON_AUTO);
        addSeasonOption(group, "Spring", SeasonalDoodleView.SEASON_SPRING);
        addSeasonOption(group, "Summer", SeasonalDoodleView.SEASON_SUMMER);
        addSeasonOption(group, "Autumn", SeasonalDoodleView.SEASON_AUTUMN);
        addSeasonOption(group, "Winter", SeasonalDoodleView.SEASON_WINTER);

        int currentSeason = prefs.getInt(OverlayPrefs.SEASON_MODE, SeasonalDoodleView.SEASON_AUTO);
        RadioButton checked = group.findViewWithTag(Integer.valueOf(currentSeason));
        if (checked == null) {
            checked = group.findViewWithTag(Integer.valueOf(SeasonalDoodleView.SEASON_AUTO));
        }
        if (checked != null) {
            checked.setChecked(true);
        }

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                View checkedView = radioGroup.findViewById(checkedId);
                Object tag = checkedView == null ? null : checkedView.getTag();
                if (tag instanceof Integer) {
                    prefs.edit().putInt(OverlayPrefs.SEASON_MODE, ((Integer) tag).intValue()).apply();
                }
            }
        });
        section.addView(group);
        return section;
    }

    private void addSeasonOption(RadioGroup group, String label, int value) {
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

    private View effectSelector() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(sectionParams);
        styleCard(section);
        section.addView(sectionTitle("Unlock effect"));
        section.addView(toggle("Unlock effect on lockscreen", OverlayPrefs.UNLOCK_EFFECT_ENABLED, true));

        int current = pendingUnlockEffect >= 0 ? pendingUnlockEffect : OverlayPrefs.unlockEffect(this);
        section.addView(sectionLabel("Stabili"));
        section.addView(effectOption(
                "S4 Lens Flare",
                "Original-style lens flare path for the S4 unlock gesture.",
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                current));
        section.addView(effectOption(
                "S5 Popping Colours",
                "Popping colour effect with screenshot-backed color map.",
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                current));
        section.addView(effectOption(
                "N4 Abstract Tiles",
                "Samsung native LockBG tile renderer with transparent screenshot composition.",
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                current));
        section.addView(effectOption(
                "NE Geometric Mosaic",
                "Samsung native LockBG mosaic renderer with transparent screenshot composition.",
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC,
                current));
        section.addView(effectOption(
                "N5 Sparkling Bubbles",
                "Samsung native bubbles renderer with cached lockscreen color sampling.",
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                current));
        section.addView(effectOption(
                "N5 Colored Droplet",
                "Ghidra-backed transparent droplet renderer with screenshot color sampling.",
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                current));

        section.addView(sectionLabel("WIP"));
        section.addView(effectOption(
                "S3 ripple WIP",
                "Reverse-backed, overlay-safe port. Background refraction is still incomplete.",
                OverlayPrefs.EFFECT_S3_RIPPLE,
                current));
        section.addView(effectOption(
                "S4 Ripple native WIP",
                "Samsung RippleInk native path kept side-by-side for future refraction work.",
                OverlayPrefs.EFFECT_S4_RIPPLE,
                current));
        section.addView(effectOption(
                "N4 Watercolor WIP",
                "Transparent app-owned watercolor renderer, still a WIP slot.",
                OverlayPrefs.EFFECT_WATERCOLOUR,
                current));

        addEffectBackgroundActions(section, current);
        return section;
    }

    private void addEffectBackgroundActions(LinearLayout section, int currentEffect) {
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
        section.addView(toggle("Auto recapture expired cache",
                OverlayPrefs.EFFECT_BACKGROUND_AUTO_REFRESH_ENABLED, false));
        section.addView(effectBackgroundIntervalSelector());
        section.addView(toggle("Pause auto recapture 23-07",
                OverlayPrefs.EFFECT_BACKGROUND_SKIP_NIGHT, true));
        section.addView(toggle("Wake lockscreen for hard recapture",
                OverlayPrefs.EFFECT_BACKGROUND_FORCE_RECAPTURE, false));
        section.addView(outlineButton("View colormap screenshot", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyPendingUnlockEffect();
                showEffectBackgroundScreenshot();
            }
        }));
    }

    private void queueUnlockEffectSelection(int value) {
        pendingUnlockEffect = value;
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
        pendingUnlockEffect = -1;
        prefs.edit().putInt(OverlayPrefs.UNLOCK_EFFECT, effect).apply();
        showTab(TAB_LOCKSCREEN_EFFECT);
    }

    private String effectBackgroundStatus(int effect) {
        if (!effectUsesColormapCache(effect)) {
            return "Screenshot cache: not used by this effect.";
        }
        File file = colormapScreenshotFileForPreview(effect);
        long capturedAt = OverlayPrefs.effectBackgroundLastCapturedAt(this, effect);
        if (capturedAt <= 0L && file.exists()) {
            capturedAt = file.lastModified();
        }
        if (!file.exists() || file.length() <= 0L || capturedAt <= 0L) {
            return "Screenshot cache: empty. The old map is kept until a valid recapture is saved.";
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - capturedAt);
        return "Screenshot cache: ready, age " + ageLabel(ageMs)
                + ". Expired cache stays active until a validated capture replaces it.";
    }

    private boolean effectUsesColormapCache(int effect) {
        return effect == OverlayPrefs.EFFECT_S3_RIPPLE
                || effect == OverlayPrefs.EFFECT_S4_RIPPLE
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
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
        group.addView(button, new RadioGroup.LayoutParams(
                0,
                dp(44),
                1f));
    }

    private View effectProfilerControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(params);
        styleProfilerCard(section);

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
        effectProfilerSummary.setPadding(0, dp(6), 0, dp(10));
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
                    + "\nThe first lockscreen gesture will be sampled automatically.";
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
        root.setBackgroundColor(COLOR_BACKGROUND);

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

    private File colormapScreenshotFileForPreview(int effect) {
        File shared = OverlayPrefs.effectBackgroundFile(this, effect);
        if (shared.exists() && shared.length() > 0L) {
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
                OverlayPrefs.EFFECT_S3_RIPPLE,
                OverlayPrefs.EFFECT_S4_RIPPLE,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
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
            if (target.exists() && !target.delete()) {
                return false;
            }
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
            if (temp.exists() && !target.exists()) {
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
        final boolean selected = value == current;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(68));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queueUnlockEffectSelection(value);
                showTab(selectedTab);
            }
        });

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        background.setColor(selected ? COLOR_ACCENT_SOFT : Color.rgb(249, 251, 253));
        background.setStroke(dp(1), selected ? COLOR_ACCENT : COLOR_DIVIDER);
        row.setBackground(background);

        View marker = new View(this);
        GradientDrawable markerBackground = new GradientDrawable();
        markerBackground.setShape(GradientDrawable.OVAL);
        markerBackground.setColor(selected ? COLOR_ACCENT : Color.TRANSPARENT);
        markerBackground.setStroke(dp(2), selected ? COLOR_ACCENT : Color.rgb(154, 166, 180));
        marker.setBackground(markerBackground);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        markerParams.setMargins(0, 0, dp(12), 0);
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
        rowParams.setMargins(0, dp(6), 0, 0);
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
        view.setPadding(0, dp(8), 0, dp(10));
        return view;
    }

    private View positionControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, dp(12));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(sectionTitle("Position offset"));

        section.addView(positionSlider("Horizontal", OverlayPrefs.POSITION_OFFSET_X, 0));
        section.addView(positionSlider("Vertical", OverlayPrefs.POSITION_OFFSET_Y, 0));
        return section;
    }

    private View doodleDebugControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, dp(12));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(sectionTitle("Debug"));
        section.addView(toggle("Rolling battery percent", OverlayPrefs.DEBUG_ROLLING_CHARGE, false));
        return section;
    }

    private View lockscreenTouchControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(sectionTitle("Touch box"));

        touchBoxSummary = new TextView(this);
        touchBoxSummary.setTextColor(COLOR_MUTED);
        touchBoxSummary.setTextSize(14f);
        touchBoxSummary.setPadding(0, dp(8), 0, dp(8));
        section.addView(touchBoxSummary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        updateTouchBoxSummary();

        section.addView(invertedToggle("Show touch box", OverlayPrefs.DEBUG_TOUCH_TRANSPARENT, true));
        section.addView(toggle("AOD standby touch box", OverlayPrefs.DEBUG_TOUCH_STANDBY, true));
        section.addView(outlineButton("Touch box screenshot wizard", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ControlActivity.this, TouchBoxSetupActivity.class);
                intent.putExtra(TouchBoxSetupActivity.EXTRA_START_CAPTURE, true);
                startActivity(intent);
            }
        }));
        section.addView(outlineButton("Reset touch box", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OverlayPrefs.clearTouchBox(ControlActivity.this);
                updateTouchBoxSummary();
            }
        }));
        return section;
    }

    private View rootDebugControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(sectionTitle("Root debug"));

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
        return section;
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
        boolean configured = OverlayPrefs.touchBoxConfigured(this);
        int left = OverlayPrefs.touchBoxLeft(this);
        int top = OverlayPrefs.touchBoxTop(this);
        int right = OverlayPrefs.touchBoxRight(this);
        int bottom = OverlayPrefs.touchBoxBottom(this);
        File screenshot = OverlayPrefs.touchBoxScreenshotFile(this);
        String cache = screenshot.exists() && screenshot.length() > 0L
                ? "screenshot cache ready"
                : "no screenshot cache";
        touchBoxSummary.setText((configured ? "Current" : "Default")
                + ": " + left + "," + top + " - " + right + "," + bottom
                + " (" + (right - left) + " x " + (bottom - top) + ")"
                + "\n" + cache);
    }

    private View positionSlider(final String label, final String key, int defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(8), 0, dp(4));
        row.setLayoutParams(rowParams);

        final TextView valueLabel = new TextView(this);
        valueLabel.setTextColor(COLOR_MUTED);
        valueLabel.setTextSize(15f);
        row.addView(valueLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar slider = new SeekBar(this);
        int range = OverlayPrefs.POSITION_OFFSET_MAX - OverlayPrefs.POSITION_OFFSET_MIN;
        int current = OverlayPrefs.clampPositionOffset(prefs.getInt(key, defaultValue));
        slider.setMax(range);
        slider.setProgress(current - OverlayPrefs.POSITION_OFFSET_MIN);
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
                dp(44)));
        return row;
    }

    private void updatePositionLabel(TextView valueLabel, String label, int value) {
        valueLabel.setText(label + ": " + signedValue(value));
    }

    private String signedValue(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
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
        button.setTextSize(14f);
        button.setTextColor(COLOR_TEXT);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(listener);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        background.setColor(COLOR_SURFACE);
        background.setStroke(dp(1), COLOR_DIVIDER);
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        params.setMargins(0, dp(8), 0, 0);
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
            accessibilityStatus.setContentDescription("Accessibility enabled. Tap to open settings.");
        } else {
            accessibilityStatus.setText("\u00d7");
            accessibilityStatus.setTextColor(COLOR_ERROR);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            return getResources().getDimensionPixelSize(id);
        }
        return dp(24);
    }
}

