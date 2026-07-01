package com.codex.chargingtouchtest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class ControlActivity extends Activity {
    private SharedPreferences prefs;
    private TextView accessibilityStatus;
    private Button accessibilityButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = OverlayPrefs.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(16, 20, 28));

        TextView title = new TextView(this);
        title.setText("Charging Touch Test");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("Passive doodle plus touch debug area");
        note.setTextColor(Color.rgb(190, 205, 220));
        note.setTextSize(16f);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(0, dp(12), 0, dp(22));
        root.addView(note, noteParams);

        root.addView(toggle("Lockscreen", OverlayPrefs.SHOW_LOCK, true));
        root.addView(toggle("AOD", OverlayPrefs.SHOW_AOD, false));
        root.addView(toggle("Home", OverlayPrefs.SHOW_HOME, false));
        root.addView(seasonSelector());
        root.addView(positionControls());
        root.addView(debugControls());

        accessibilityStatus = new TextView(this);
        accessibilityStatus.setTextSize(16f);
        accessibilityStatus.setGravity(Gravity.CENTER);
        accessibilityStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(8), 0, dp(4));
        root.addView(accessibilityStatus, statusParams);

        accessibilityButton = button("Open accessibility settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        root.addView(accessibilityButton);
        updateAccessibilityStatus();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(16, 20, 28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    private Switch toggle(String label, final String key, boolean defaultValue) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(Color.WHITE);
        toggle.setTextSize(18f);
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

    private View seasonSelector() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(0, dp(18), 0, dp(10));
        section.setLayoutParams(sectionParams);

        TextView label = new TextView(this);
        label.setText("Seasonal doodle");
        label.setTextColor(Color.rgb(220, 232, 242));
        label.setTextSize(18f);
        section.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

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
        button.setTextColor(Color.WHITE);
        button.setTextSize(17f);
        button.setTag(Integer.valueOf(value));
        button.setPadding(0, dp(5), 0, dp(5));
        group.addView(button, new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT,
                dp(44)));
    }

    private View positionControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, dp(14));
        section.setLayoutParams(params);

        TextView label = new TextView(this);
        label.setText("Position offset");
        label.setTextColor(Color.rgb(220, 232, 242));
        label.setTextSize(18f);
        section.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        section.addView(positionSlider("Horizontal", OverlayPrefs.POSITION_OFFSET_X, 0));
        section.addView(positionSlider("Vertical", OverlayPrefs.POSITION_OFFSET_Y, 0));
        return section;
    }

    private View debugControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(12));
        section.setLayoutParams(params);

        TextView label = new TextView(this);
        label.setText("Debug");
        label.setTextColor(Color.rgb(220, 232, 242));
        label.setTextSize(18f);
        section.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        section.addView(toggle("Charging doodle overlay", OverlayPrefs.SHOW_DOODLE, true));
        section.addView(toggle("Rolling battery percent", OverlayPrefs.DEBUG_ROLLING_CHARGE, false));
        section.addView(toggle("Touch debug area", OverlayPrefs.DEBUG_TOUCH_AREA, true));
        section.addView(toggle("Transparent touch area", OverlayPrefs.DEBUG_TOUCH_TRANSPARENT, true));
        section.addView(toggle("Loop lens flare in touch box", OverlayPrefs.DEBUG_LENS_LOOP, true));
        section.addView(button("Calibrate touch box", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ControlActivity.this, TouchBoxSetupActivity.class));
            }
        }));
        section.addView(button("Reset touch box", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OverlayPrefs.clearTouchBox(ControlActivity.this);
            }
        }));
        return section;
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
        valueLabel.setTextColor(Color.rgb(190, 205, 220));
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

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        params.setMargins(0, dp(7), 0, dp(7));
        button.setLayoutParams(params);
        return button;
    }

    private void updateAccessibilityStatus() {
        if (accessibilityStatus == null || accessibilityButton == null) {
            return;
        }
        if (isChargingAccessibilityEnabled()) {
            accessibilityStatus.setText("Accessibilita OK");
            accessibilityStatus.setTextColor(Color.rgb(85, 230, 145));
            accessibilityButton.setText("Gestisci accessibilita");
        } else {
            accessibilityStatus.setText("Devi attivare accessibilita: premi il tasto qui sotto.");
            accessibilityStatus.setTextColor(Color.rgb(255, 178, 105));
            accessibilityButton.setText("Attiva accessibilita");
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
}

