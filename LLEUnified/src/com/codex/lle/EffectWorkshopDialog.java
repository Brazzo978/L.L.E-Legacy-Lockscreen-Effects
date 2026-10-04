package com.codex.lle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.PopupWindow;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Draft editor: only Apply writes preferences; Cancel discards every edit. */
final class EffectWorkshopDialog {
    private static final int ACCENT = Color.rgb(0, 132, 142);
    private static final int SOFT = Color.rgb(231, 247, 248);
    private static final int INK = Color.rgb(33, 49, 58);
    private static final int MUTED = Color.rgb(102, 123, 131);
    private EffectWorkshopDialog() {}

    static void show(final Activity activity, int selectedEffect) {
        if (EffectWorkshopConfig.parametersFor(selectedEffect).length == 0) {
            new AlertDialog.Builder(activity).setTitle(OverlayPrefs.effectLabel(selectedEffect))
                    .setMessage("Advanced controls for this effect are being added.")
                    .setPositiveButton("Close", null).show();
            return;
        }
        edit(activity, selectedEffect);
    }

    private static void edit(final Activity activity, final int effect) {
        final EffectWorkshopConfig.Parameter[] parameters = EffectWorkshopConfig.parametersFor(effect);
        EffectWorkshopConfig.Values saved = EffectWorkshopPrefs.values(activity, effect);
        final Map<String, Float> draftBase = new LinkedHashMap<String, Float>(saved.configuredValues());
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 12);
        content.setPadding(padding, dp(activity, 4), padding, dp(activity, 8));
        TextView intro = new TextView(activity);
        intro.setText("Drag or tap − / + to adjust; tap the value to type. Apply saves your changes. ⓘ explains each control.");
        intro.setTextColor(MUTED);
        intro.setTextSize(13);
        intro.setLineSpacing(dp(activity, 1), 1);
        content.addView(intro);
        if (!OverlayPrefs.masterEnabled(activity) || !ChargingAccessibilityService.isRuntimeServiceConnected()) {
            TextView runtimeNote = new TextView(activity);
            String appName = BuildFlavor.TESTER ? "L.L.E Tester" : "L.L.E";
            runtimeNote.setText(!OverlayPrefs.masterEnabled(activity)
                    ? "Effects are paused in " + appName + ". Enable the app on its main screen to see your changes."
                    : appName + "'s accessibility service is not connected. Enable it in Android Accessibility settings to see your changes.");
            runtimeNote.setPadding(0, padding, 0, 0);
            runtimeNote.setTextColor(MUTED);
            content.addView(runtimeNote);
        }
        final Switch enabled = new Switch(activity);
        enabled.setText("Enable custom settings");
        enabled.setTextColor(INK);
        enabled.setTextSize(14);
        enabled.setThumbTintList(new ColorStateList(new int[][] {
                new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {ACCENT, Color.rgb(157, 174, 180)}));
        enabled.setTrackTintList(new ColorStateList(new int[][] {
                new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {Color.rgb(163, 216, 219), Color.rgb(218, 228, 231)}));
        enabled.setChecked(saved.enabled);
        enabled.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
        enabled.setMinimumHeight(dp(activity, 48));
        content.addView(enabled);
        final ArrayList<Row> rows = new ArrayList<Row>();
        String group = "";
        for (EffectWorkshopConfig.Parameter parameter : parameters) {
            if (effect == OverlayPrefs.EFFECT_LG_G1_HULA_HOOP) {
                boolean fluidic = OverlayPrefs.hulaHoopVariant(activity)
                        == OverlayPrefs.HULA_HOOP_VARIANT_V2;
                if ((fluidic && parameter.key.startsWith("v1_"))
                        || (!fluidic && parameter.key.startsWith("v2_"))) continue;
            }
            if (effect == OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE) {
                boolean nativeRevision = OverlayPrefs.g2LightParticleRevision(activity)
                        == OverlayPrefs.G2_LIGHT_PARTICLE_REVISION_V2;
                if ((nativeRevision && parameter.key.contains("_v1_"))
                        || (!nativeRevision && parameter.key.contains("_v2_"))) continue;
            }
            if (!parameter.group.equals(group)) {
                group = parameter.group;
                TextView heading = new TextView(activity);
                heading.setText(group);
                heading.setTextSize(16);
                heading.setTextColor(ACCENT);
                heading.setTypeface(null, android.graphics.Typeface.BOLD);
                heading.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
                content.addView(heading);
            }
            Row row = new Row(activity, parameter, saved.configuredValue(parameter.key),
                    new Runnable() {
                        @Override public void run() { enabled.setChecked(true); }
                    });
            rows.add(row);
            content.addView(row.view);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(surface(activity, Color.rgb(238, 246, 251), 20, 0));
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(padding, dp(activity, 10), padding, dp(activity, 6));
        TextView eyebrow = new TextView(activity);
        eyebrow.setText("EFFECT WORKSHOP");
        eyebrow.setTextSize(10);
        eyebrow.setLetterSpacing(.14f);
        eyebrow.setTextColor(ACCENT);
        header.addView(eyebrow);
        TextView effectTitle = new TextView(activity);
        effectTitle.setText(OverlayPrefs.effectLabel(effect));
        effectTitle.setTextSize(20);
        effectTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        effectTitle.setTextColor(INK);
        effectTitle.setPadding(0, dp(activity, 2), 0, 0);
        header.addView(effectTitle);
        body.addView(header);
        body.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(padding, dp(activity, 6), padding, dp(activity, 8));
        View divider = new View(activity);
        divider.setBackgroundColor(Color.rgb(214, 229, 231));
        body.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)));
        body.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final Button apply = actionButton(activity,
                enabled.isChecked() ? "Apply custom settings" : "Use original settings", true);
        footer.addView(apply, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout secondary = new LinearLayout(activity);
        LinearLayout.LayoutParams secondaryLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secondaryLayout.topMargin = dp(activity, 6);
        footer.addView(secondary, secondaryLayout);
        Button cancel = actionButton(activity, "Cancel", false);
        Button resetAll = actionButton(activity, "Reset all", false);
        LinearLayout.LayoutParams cancelLayout = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cancelLayout.rightMargin = dp(activity, 8);
        secondary.addView(cancel, cancelLayout);
        secondary.addView(resetAll, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(body).create();
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                apply.setText(checked ? "Apply custom settings" : "Use original settings");
            }
        });
        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Map<String, Float> draft = new LinkedHashMap<String, Float>(draftBase);
                Row firstInvalid = null;
                for (Row row : rows) {
                    Float value = row.read();
                    if (value == null) { if (firstInvalid == null) firstInvalid = row; }
                    else draft.put(row.parameter.key, value);
                }
                if (firstInvalid != null) { firstInvalid.value.requestFocus(); return; }
                EffectWorkshopPrefs.save(activity, EffectWorkshopConfig.create(effect,
                        enabled.isChecked(), draft));
                Toast.makeText(activity, enabled.isChecked() ? "Custom settings applied"
                        : "Original effect applied; custom values kept", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });
        resetAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                enabled.setChecked(false);
                draftBase.clear();
                for (EffectWorkshopConfig.Parameter p : parameters) draftBase.put(p.key, p.defaultValue);
                for (Row row : rows) row.setValue(row.parameter.defaultValue);
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(surface(activity,
                    Color.rgb(238, 246, 251), 20, 0));
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams
                    .SOFT_INPUT_ADJUST_RESIZE | android.view.WindowManager.LayoutParams
                    .SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * .85f));
        }
    }

    private static Button actionButton(Activity activity, String label, boolean primary) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(0, 132, 142));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(activity, 48));
        button.setMinimumHeight(dp(activity, 48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(activity, 10), dp(activity, 6), dp(activity, 10), dp(activity, 6));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(activity, 10));
        background.setColor(primary ? Color.rgb(0, 132, 142) : Color.rgb(231, 247, 248));
        if (!primary) background.setStroke(dp(activity, 1), Color.rgb(190, 222, 225));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(
                primary ? Color.argb(60, 255, 255, 255) : Color.argb(35, 0, 132, 142)),
                background, null));
        button.setElevation(0);
        button.setStateListAnimator(null);
        return button;
    }

    private static GradientDrawable surface(Activity activity, int color, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(activity, radius));
        if (stroke != 0) background.setStroke(dp(activity, 1), stroke);
        return background;
    }

    private static final class Row {
        final EffectWorkshopConfig.Parameter parameter;
        final LinearLayout view;
        final EditText value;
        final SeekBar slider;
        final WorkshopSliderScale scale;
        final Button minus, plus;
        long lastTick;
        boolean syncing;

        Row(final Activity activity, final EffectWorkshopConfig.Parameter parameter, float initial,
                final Runnable onEdit) {
            this.parameter = parameter;
            scale = new WorkshopSliderScale(parameter.min, parameter.max, parameter.step);
            view = new LinearLayout(activity);
            view.setOrientation(LinearLayout.VERTICAL);
            int inset = dp(activity, 10);
            view.setPadding(inset, dp(activity, 2), inset, dp(activity, 6));
            view.setBackground(surface(activity, Color.WHITE, 12, Color.rgb(214, 229, 233)));
            LinearLayout.LayoutParams cardLayout = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLayout.bottomMargin = dp(activity, 8);
            view.setLayoutParams(cardLayout);
            LinearLayout title = new LinearLayout(activity);
            title.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(activity);
            label.setText(parameter.label);
            label.setTextColor(INK);
            label.setTextSize(14);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            title.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button reset = actionButton(activity, "↺", false);
            reset.setTextSize(20);
            reset.setPadding(0, 0, 0, 0);
            reset.setIncludeFontPadding(false);
            reset.setBackground(new RippleDrawable(ColorStateList.valueOf(
                    Color.argb(35, 0, 132, 142)), null,
                    surface(activity, Color.WHITE, 10, 0)));
            reset.setContentDescription("Reset " + parameter.label + " to default "
                    + format(parameter.defaultValue) + " " + parameter.unit);
            if (Build.VERSION.SDK_INT >= 26) reset.setTooltipText("Reset default");
            reset.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setValue(parameter.defaultValue);
                    onEdit.run();
                }
            });
            title.addView(reset, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            Button help = actionButton(activity, "ⓘ", false);
            help.setText("\u24d8");
            help.setTextSize(18);
            help.setPadding(0, 0, 0, 0);
            help.setIncludeFontPadding(false);
            help.setBackground(new RippleDrawable(ColorStateList.valueOf(
                    Color.argb(35, 0, 132, 142)), null,
                    surface(activity, Color.WHITE, 10, 0)));
            help.setMinWidth(dp(activity, 48));
            help.setMinimumWidth(dp(activity, 48));
            help.setContentDescription("About " + parameter.label);
            help.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View anchor) {
                    showHelp(activity, anchor, parameter);
                }
            });
            title.addView(help, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            view.addView(title);
            LinearLayout input = new LinearLayout(activity);
            input.setGravity(Gravity.CENTER_VERTICAL);
            minus = actionButton(activity, "−", false);
            plus = actionButton(activity, "+", false);
            minus.setTextSize(20);
            plus.setTextSize(20);
            minus.setPadding(0, 0, 0, 0);
            plus.setPadding(0, 0, 0, 0);
            minus.setIncludeFontPadding(false);
            plus.setIncludeFontPadding(false);
            minus.setContentDescription("Decrease " + parameter.label + " by " + format(parameter.step));
            plus.setContentDescription("Increase " + parameter.label + " by " + format(parameter.step));
            input.addView(minus, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            LinearLayout valueBadge = new LinearLayout(activity);
            valueBadge.setOrientation(LinearLayout.HORIZONTAL);
            valueBadge.setGravity(Gravity.CENTER);
            valueBadge.setPadding(dp(activity, 4), 0, dp(activity, 6), 0);
            valueBadge.setBackground(surface(activity, SOFT, 12, 0));
            LinearLayout.LayoutParams badgeLayout = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            badgeLayout.setMargins(dp(activity, 6), 0, dp(activity, 6), 0);
            input.addView(valueBadge, badgeLayout);
            value = new EditText(activity);
            value.setSingleLine(true);
            value.setTextSize(18);
            value.setTextColor(ACCENT);
            value.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
            value.setGravity(Gravity.CENTER);
            value.setBackgroundColor(Color.TRANSPARENT);
            value.setPadding(dp(activity, 2), 0, dp(activity, 2), 0);
            value.setIncludeFontPadding(false);
            value.setMinHeight(dp(activity, 48));
            value.setContentDescription(parameter.label + " value");
            value.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
            valueBadge.addView(value, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView unit = new TextView(activity);
            unit.setText(parameter.unit);
            unit.setTextSize(10);
            unit.setTextColor(MUTED);
            unit.setGravity(Gravity.CENTER);
            unit.setMaxWidth(dp(activity, 64));
            valueBadge.addView(unit);
            input.addView(plus, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            minus.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { nudge(-1, onEdit); }
            });
            plus.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { nudge(1, onEdit); }
            });
            view.addView(input);
            slider = new SeekBar(activity);
            slider.setMax(scale.steps);
            slider.setContentDescription(parameter.label + " slider, step " + format(parameter.step));
            slider.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            slider.setSplitTrack(false);
            GradientDrawable track = surface(activity, Color.rgb(216, 233, 235), 3, 0);
            track.setSize(dp(activity, 100), dp(activity, 6));
            GradientDrawable fill = surface(activity, ACCENT, 3, 0);
            fill.setSize(dp(activity, 100), dp(activity, 6));
            LayerDrawable progress = new LayerDrawable(new android.graphics.drawable.Drawable[] {
                    track, new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL)});
            progress.setId(0, android.R.id.background);
            progress.setId(1, android.R.id.progress);
            slider.setProgressDrawable(progress);
            GradientDrawable thumb = new GradientDrawable();
            thumb.setShape(GradientDrawable.OVAL);
            thumb.setColor(Color.WHITE);
            thumb.setStroke(dp(activity, 2), ACCENT);
            thumb.setSize(dp(activity, 20), dp(activity, 20));
            slider.setThumb(thumb);
            slider.setThumbOffset(dp(activity, 10));
            slider.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
            if (Build.VERSION.SDK_INT >= 24 && scale.steps <= 30) {
                GradientDrawable tick = new GradientDrawable();
                tick.setShape(GradientDrawable.OVAL);
                tick.setColor(Color.rgb(134, 191, 197));
                tick.setSize(dp(activity, 3), dp(activity, 3));
                slider.setTickMark(tick);
            }
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser && !syncing) {
                        setValue(scale.valueAt(progress));
                        long now = SystemClock.uptimeMillis();
                        if (now - lastTick >= 45) {
                            bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            lastTick = now;
                        }
                        onEdit.run();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            view.addView(slider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48)));
            LinearLayout bounds = new LinearLayout(activity);
            TextView minimum = new TextView(activity);
            minimum.setText(format(parameter.min));
            minimum.setTextColor(MUTED);
            minimum.setTextSize(11);
            bounds.addView(minimum, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView hint = new TextView(activity);
            hint.setText("Default " + format(parameter.defaultValue) + " · Step " + format(parameter.step));
            hint.setTextSize(10);
            hint.setTextColor(MUTED);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
            bounds.addView(hint, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 4));
            TextView maximum = new TextView(activity);
            maximum.setText(format(parameter.max));
            maximum.setTextColor(MUTED);
            maximum.setTextSize(11);
            maximum.setGravity(Gravity.RIGHT);
            bounds.addView(maximum, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            view.addView(bounds);
            value.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean focused) {
                    if (!focused) {
                        Float parsed = read();
                        if (parsed != null) setValue(parsed);
                    }
                }
            });
            setValue(initial);
            value.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!syncing) {
                        try {
                            float parsed = Float.parseFloat(s.toString().trim().replace(',', '.'));
                            if (EffectWorkshopConfig.finite(parsed)) updateNudges(parsed);
                        } catch (NumberFormatException ignored) {
                            // Keep editing incomplete input without showing an error until validation.
                            minus.setEnabled(true);
                            plus.setEnabled(true);
                            minus.setAlpha(1);
                            plus.setAlpha(1);
                        }
                        onEdit.run();
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        void setValue(float number) {
            syncing = true;
            value.setText(format(number));
            value.setError(null);
            slider.setProgress(scale.progressFor(number));
            updateNudges(number);
            syncing = false;
        }

        void updateNudges(float number) {
            minus.setEnabled(number > parameter.min);
            plus.setEnabled(number < parameter.max);
            minus.setAlpha(number > parameter.min ? 1 : .4f);
            plus.setAlpha(number < parameter.max ? 1 : .4f);
        }

        void nudge(int direction, Runnable onEdit) {
            Float current = read();
            if (current == null) { value.requestFocus(); return; }
            float next = scale.nudge(current, direction);
            if (next == current) return;
            setValue(next);
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            onEdit.run();
        }

        Float read() {
            try {
                float parsed = Float.parseFloat(value.getText().toString().trim().replace(',', '.'));
                if (!EffectWorkshopConfig.finite(parsed) || parsed < parameter.min
                        || parsed > parameter.max || (parameter.integer && parsed != Math.round(parsed))) {
                    throw new NumberFormatException();
                }
                value.setError(null);
                return parsed;
            } catch (NumberFormatException invalid) {
                value.setError("Enter " + (parameter.integer ? "a whole number" : "a number")
                        + " from " + format(parameter.min) + " to " + format(parameter.max));
                return null;
            }
        }
    }

    private static void showHelp(final Activity activity, View anchor,
            EffectWorkshopConfig.Parameter parameter) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 16);
        body.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(activity, 12));
        background.setStroke(dp(activity, 1), Color.rgb(190, 204, 218));
        body.setBackground(background);
        TextView title = new TextView(activity);
        title.setText(parameter.label);
        title.setTextColor(Color.rgb(30, 44, 60));
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(title);
        TextView explanation = new TextView(activity);
        explanation.setText(parameter.description + "\n\nDefault: "
                + format(parameter.defaultValue) + " " + parameter.unit
                + "\nRange: " + format(parameter.min) + " – " + format(parameter.max)
                + " " + parameter.unit);
        explanation.setTextColor(Color.rgb(45, 59, 76));
        explanation.setTextSize(15);
        explanation.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
        body.addView(explanation);
        int width = Math.min(dp(activity, 330),
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32));
        Rect visible = new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);
        int availableHeight = visible.height() > 0 ? visible.height()
                : activity.getResources().getDisplayMetrics().heightPixels;
        final ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final PopupWindow popup = new PopupWindow(scroll, width,
                Math.min(body.getMeasuredHeight() + dp(activity, 60),
                        Math.max(dp(activity, 80), availableHeight - dp(activity, 32))), true);
        popup.setBackgroundDrawable(background);
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(activity, 8));
        Button close = new Button(activity);
        close.setText("Close");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { popup.dismiss(); }
        });
        body.addView(close);
        popup.showAsDropDown(anchor, -Math.max(0, width - anchor.getWidth()), 0);
    }

    private static String format(float value) {
        return new java.math.BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }

    private static int dp(Activity activity, int size) {
        return Math.round(size * activity.getResources().getDisplayMetrics().density);
    }
}
