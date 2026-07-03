package com.codex.lle;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

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

    private SharedPreferences prefs;
    private TextView accessibilityStatus;
    private Button accessibilityButton;
    private Switch serviceSwitch;
    private Button chargingDoodleTabButton;
    private Button lockscreenEffectTabButton;
    private LinearLayout tabContent;
    private TextView touchBoxSummary;
    private int selectedTab = TAB_CHARGING_DOODLE;
    private boolean updatingServiceSwitch;

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
        showTab(selectedTab);

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
        outer.addView(accessibilityFooter(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
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
                if (!isChargingAccessibilityEnabled()) {
                    Toast.makeText(ControlActivity.this,
                            "Enable accessibility first", Toast.LENGTH_SHORT).show();
                    openAccessibilitySettings();
                    updateAccessibilityStatus();
                    return;
                }
                if (!isChecked) {
                    Toast.makeText(ControlActivity.this,
                            "Disable the service from Accessibility settings",
                            Toast.LENGTH_SHORT).show();
                    updateAccessibilityStatus();
                }
            }
        });
        row.addView(serviceSwitch, new LinearLayout.LayoutParams(
                dp(62),
                dp(48)));

        accessibilityStatus = new TextView(this);
        accessibilityStatus.setGravity(Gravity.CENTER);
        accessibilityStatus.setTextSize(22f);
        accessibilityStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                dp(30),
                dp(44));
        statusParams.setMargins(dp(4), 0, 0, 0);
        row.addView(accessibilityStatus, statusParams);
        return header;
    }

    private View accessibilityFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(16), dp(10), dp(16), dp(16));
        footer.setBackgroundColor(COLOR_SURFACE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            footer.setElevation(dp(4));
        }
        accessibilityButton = outlineButton("Settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        footer.addView(accessibilityButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));
        return footer;
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
        selectedTab = tab == TAB_LOCKSCREEN_EFFECT ? TAB_LOCKSCREEN_EFFECT : TAB_CHARGING_DOODLE;
        updateTabStyles();
        if (tabContent == null) {
            return;
        }
        tabContent.removeAllViews();
        if (selectedTab == TAB_CHARGING_DOODLE) {
            tabContent.addView(chargingDoodleControls());
        } else {
            tabContent.addView(effectSelector());
            tabContent.addView(lockscreenTouchControls());
        }
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

    private TextView sectionTitle(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(18f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, 0, 0, dp(8));
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
        section.addView(toggle("Doodle on AOD", OverlayPrefs.SHOW_AOD, false));
        section.addView(toggle("Doodle on Home", OverlayPrefs.SHOW_HOME, false));
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

        int current = OverlayPrefs.unlockEffect(this);
        section.addView(effectOption(
                "S3 ripple WIP",
                "Reverse-backed, overlay-safe port. Not millimeter-exact because Samsung fullscreen refraction is disabled.",
                OverlayPrefs.EFFECT_S3_RIPPLE,
                current));
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
                "N4 Watercolor WIP",
                "Transparent app-owned watercolor renderer, still a WIP slot.",
                OverlayPrefs.EFFECT_WATERCOLOUR,
                current));

        section.addView(outlineButton("Refresh effect background map", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int token = prefs.getInt(OverlayPrefs.POPPING_COLOR_REFRESH_TOKEN, 0);
                prefs.edit()
                        .putInt(OverlayPrefs.POPPING_COLOR_REFRESH_TOKEN, token + 1)
                        .apply();
                Toast.makeText(ControlActivity.this,
                        "Effect background refresh queued",
                        Toast.LENGTH_SHORT).show();
            }
        }));
        return section;
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
                prefs.edit().putInt(OverlayPrefs.UNLOCK_EFFECT, value).apply();
                showTab(TAB_LOCKSCREEN_EFFECT);
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
        if (accessibilityStatus == null || accessibilityButton == null || serviceSwitch == null) {
            return;
        }
        boolean enabled = isChargingAccessibilityEnabled();
        updatingServiceSwitch = true;
        serviceSwitch.setChecked(enabled);
        serviceSwitch.setEnabled(true);
        serviceSwitch.setText("");
        updatingServiceSwitch = false;
        if (enabled) {
            accessibilityStatus.setText("\u2713");
            accessibilityStatus.setTextColor(COLOR_OK);
            accessibilityButton.setText("Settings");
        } else {
            accessibilityStatus.setText("\u00d7");
            accessibilityStatus.setTextColor(COLOR_ERROR);
            accessibilityButton.setText("Enable");
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

