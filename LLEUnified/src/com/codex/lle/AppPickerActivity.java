package com.codex.lle;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Safe package picker for the existing runtime-surface blacklist. */
public final class AppPickerActivity extends Activity {
    static final String EXTRA_MODE = "picker_mode";
    static final String MODE_EXCLUSIONS = "exclusions";
    static final String MODE_LOCKSCREEN_ALLOWLIST = "lockscreen_allowlist";
    private static final int COLOR_BACKGROUND = Color.rgb(238, 246, 251);
    private static final int COLOR_TEXT = Color.rgb(33, 33, 33);
    private static final int COLOR_MUTED = Color.rgb(117, 117, 117);
    private static final int COLOR_ACCENT = Color.rgb(0, 132, 142);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(231, 247, 248);

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final List<AppPickerModel.Entry> allApps =
            new ArrayList<AppPickerModel.Entry>();
    private final Map<String, Drawable> icons = new HashMap<String, Drawable>();

    private LinearLayout appList;
    private TextView summary;
    private ProgressBar progress;
    private Button allButton;
    private Button userButton;
    private Button systemButton;
    private int category = AppPickerModel.CATEGORY_ALL;
    private String query = "";
    private boolean allowlistMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureBars();
        allowlistMode = MODE_LOCKSCREEN_ALLOWLIST.equals(
                getIntent().getStringExtra(EXTRA_MODE));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        root.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34f, COLOR_ACCENT, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(allowlistMode
                ? "Allowed lockscreen apps" : "Choose excluded apps",
                22f, COLOR_TEXT, true));
        summary = text("Finding launchable applications…", 13f, COLOR_MUTED, false);
        titles.addView(summary);
        titleRow.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(titleRow);

        TextView explanation = text(allowlistMode
                ? "When automatic protection is enabled, selected apps may remain above "
                        + "the lockscreen without hiding L.L.E. Core lockscreen surfaces "
                        + "are always allowed."
                : "Selected apps suppress every L.L.E. runtime surface while they appear "
                        + "over the lockscreen. L.L.E. safety rules stay protected.",
                13f, COLOR_MUTED, false);
        explanation.setPadding(dp(4), 0, dp(4), dp(10));
        root.addView(explanation);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search name or package");
        search.setTextColor(COLOR_TEXT);
        search.setHintTextColor(COLOR_MUTED);
        search.setTextSize(14f);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(rounded(Color.WHITE, dp(13), Color.argb(70, 0, 132, 142)));
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                query = value == null
                        ? "" : value.toString().trim().toLowerCase(Locale.US);
                render();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, dp(10), 0, dp(10));
        allButton = categoryButton("All", AppPickerModel.CATEGORY_ALL);
        userButton = categoryButton("User", AppPickerModel.CATEGORY_USER);
        systemButton = categoryButton("System", AppPickerModel.CATEGORY_SYSTEM);
        filters.addView(allButton, weightedButtonParams());
        filters.addView(userButton, weightedButtonParams());
        filters.addView(systemButton, weightedButtonParams());
        root.addView(filters);
        updateFilterStyles();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(appList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        TextView fallback = text(allowlistMode
                ? "Only add an app here if automatic protection hides L.L.E. unnecessarily."
                : "App missing? Return to App exclusions and enter its package name manually.",
                12f, COLOR_MUTED, false);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(0, dp(8), 0, 0);
        root.addView(fallback);

        setContentView(root);
        loadApplications();
    }

    private void configureBars() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void loadApplications() {
        progress.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppPickerModel.Entry> loaded =
                        new ArrayList<AppPickerModel.Entry>();
                final Map<String, Drawable> loadedIcons =
                        new HashMap<String, Drawable>();
                PackageManager manager = getPackageManager();
                Set<String> selected = allowlistMode
                        ? OverlayPrefs.userLockscreenAllowlistPackages(AppPickerActivity.this)
                        : OverlayPrefs.userRuntimeBlacklistPackages(AppPickerActivity.this);
                Map<String, ApplicationInfo> visible =
                        new LinkedHashMap<String, ApplicationInfo>();

                Intent launcher = new Intent(Intent.ACTION_MAIN);
                launcher.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> launchers = manager.queryIntentActivities(launcher, 0);
                for (int i = 0; i < launchers.size(); i++) {
                    ResolveInfo resolve = launchers.get(i);
                    if (resolve.activityInfo != null
                            && resolve.activityInfo.applicationInfo != null) {
                        ApplicationInfo info = resolve.activityInfo.applicationInfo;
                        visible.put(info.packageName, info);
                    }
                }

                Set<String> explicit = new HashSet<String>(selected);
                explicit.add(getPackageName());
                explicit.add("com.android.systemui");
                explicit.add("com.samsung.android.app.aodservice");
                for (String packageName : explicit) {
                    if (visible.containsKey(packageName)) {
                        continue;
                    }
                    try {
                        visible.put(packageName, manager.getApplicationInfo(packageName, 0));
                    } catch (PackageManager.NameNotFoundException ignored) {
                        if (selected.contains(packageName)) {
                            loaded.add(new AppPickerModel.Entry(
                                    packageName, packageName, false, true, false));
                        }
                    }
                }

                for (ApplicationInfo info : visible.values()) {
                    String packageName = OverlayPrefs.normalizePackageName(info.packageName);
                    boolean builtIn = ChargingAccessibilityService
                            .isBuiltInRuntimeBlacklistPackage(packageName);
                    boolean protectedByLle = allowlistMode
                            ? RuntimeBlacklistPolicy.isCoreProtectedPackage(
                                    packageName, getPackageName())
                            : RuntimeBlacklistPolicy.isProtectedPackage(
                                    packageName, getPackageName(), builtIn);
                    boolean selectedByPolicy = allowlistMode && protectedByLle;
                    boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    CharSequence label = manager.getApplicationLabel(info);
                    loaded.add(new AppPickerModel.Entry(
                            label == null ? packageName : label.toString(),
                            packageName,
                            system,
                            selected.contains(packageName) || selectedByPolicy,
                            protectedByLle));
                    try {
                        loadedIcons.put(packageName, manager.getApplicationIcon(info));
                    } catch (RuntimeException ignored) {
                    }
                }

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        allApps.clear();
                        allApps.addAll(loaded);
                        icons.clear();
                        icons.putAll(loadedIcons);
                        progress.setVisibility(View.GONE);
                        render();
                    }
                });
            }
        }, "lle-app-picker").start();
    }

    private void render() {
        if (appList == null) {
            return;
        }
        List<AppPickerModel.Entry> visible =
                AppPickerModel.filterAndSort(allApps, category, query);
        appList.removeAllViews();
        int selectedCount = 0;
        for (int i = 0; i < allApps.size(); i++) {
            if (allApps.get(i).selected) {
                selectedCount++;
            }
        }
        summary.setText(visible.size() + " shown · " + selectedCount + " selected");
        if (visible.isEmpty()) {
            TextView empty = text(allApps.isEmpty()
                    ? "No visible applications found."
                    : "No applications match this filter.", 14f, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, dp(40));
            appList.addView(empty);
            return;
        }
        for (int i = 0; i < visible.size(); i++) {
            appList.addView(appRow(visible.get(i)));
        }
    }

    private View appRow(final AppPickerModel.Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(10), dp(9));
        row.setBackground(rounded(entry.selected
                ? COLOR_ACCENT_SOFT : Color.WHITE, dp(12),
                entry.selected ? COLOR_ACCENT : Color.argb(28, 0, 0, 0)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(icons.get(entry.packageName));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(entry.label, 15f,
                entry.protectedByLle ? COLOR_MUTED : COLOR_TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        String detail = entry.packageName;
        if (entry.protectedByLle) {
            detail += allowlistMode ? " · Always allowed" : " · Protected by L.L.E";
        } else if (entry.system) {
            detail += " · System";
        }
        TextView packageView = text(detail, 11f, COLOR_MUTED, false);
        packageView.setSingleLine(true);
        packageView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        labels.addView(packageView);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        CheckBox check = new CheckBox(this);
        check.setChecked(entry.selected || entry.protectedByLle);
        check.setEnabled(!entry.protectedByLle);
        check.setClickable(false);
        row.addView(check);

        if (!entry.protectedByLle) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggle(entry);
                }
            });
        }
        return row;
    }

    private void toggle(AppPickerModel.Entry entry) {
        Set<String> packages = allowlistMode
                ? OverlayPrefs.userLockscreenAllowlistPackages(this)
                : OverlayPrefs.userRuntimeBlacklistPackages(this);
        boolean selected;
        if (packages.contains(entry.packageName)) {
            packages.remove(entry.packageName);
            selected = false;
        } else {
            packages.add(entry.packageName);
            selected = true;
        }
        if (allowlistMode) {
            OverlayPrefs.setUserLockscreenAllowlistPackages(this, packages);
        } else {
            OverlayPrefs.setUserRuntimeBlacklistPackages(this, packages);
        }
        for (int i = 0; i < allApps.size(); i++) {
            AppPickerModel.Entry current = allApps.get(i);
            if (current.packageName.equals(entry.packageName)) {
                allApps.set(i, new AppPickerModel.Entry(
                        current.label, current.packageName, current.system,
                        selected, current.protectedByLle));
                break;
            }
        }
        Toast.makeText(this, selected
                ? allowlistMode
                        ? entry.label + " allowed on lockscreen"
                        : "L.L.E. will hide over " + entry.label
                : entry.label + " removed", Toast.LENGTH_SHORT).show();
        render();
    }

    private Button categoryButton(String title, final int value) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(12f);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                category = value;
                updateFilterStyles();
                render();
            }
        });
        return button;
    }

    private void updateFilterStyles() {
        styleFilter(allButton, category == AppPickerModel.CATEGORY_ALL);
        styleFilter(userButton, category == AppPickerModel.CATEGORY_USER);
        styleFilter(systemButton, category == AppPickerModel.CATEGORY_SYSTEM);
    }

    private void styleFilter(Button button, boolean selected) {
        button.setTextColor(selected ? COLOR_ACCENT : COLOR_MUTED);
        button.setTypeface(Typeface.DEFAULT,
                selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(rounded(
                selected ? COLOR_ACCENT_SOFT : Color.WHITE,
                dp(10),
                selected ? COLOR_ACCENT : Color.argb(28, 0, 0, 0)));
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
