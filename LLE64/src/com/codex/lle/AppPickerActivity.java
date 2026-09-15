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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppPickerActivity extends Activity {
    private static final int CATEGORY_ALL = 0;
    private static final int CATEGORY_USER = 1;
    private static final int CATEGORY_SYSTEM = 2;

    private static final int COLOR_NAVY = Color.rgb(20, 38, 58);
    private static final int COLOR_TEXT = Color.rgb(33, 33, 33);
    private static final int COLOR_MUTED = Color.rgb(117, 117, 117);
    private static final int COLOR_ACCENT = Color.rgb(0, 132, 142);
    private static final int COLOR_ACCENT_BG = Color.argb(25, 0, 132, 142);

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> allApps = new ArrayList<AppEntry>();
    private Set<String> blacklistedPackages = new HashSet<String>();

    private LinearLayout headerLayout;
    private LinearLayout bottomLoadingContainer;
    private LinearLayout appListContainer;
    private TextView countTextView;
    private ProgressBar bottomProgressBar;
    private TextView bottomDiscoverTextView;
    private Button btnCategoryAll;
    private Button btnCategoryUser;
    private Button btnCategorySystem;

    private int currentCategory = CATEGORY_ALL;
    private String currentSearchQuery = "";
    private final Set<String> knownCameraPackages = new HashSet<String>();
    private final Set<String> knownMusicPackages = new HashSet<String>();

    private static class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;
        final boolean isSystem;
        final boolean isCamera;
        final boolean isMusic;
        final boolean isEmergency;
        final boolean isClock;

        AppEntry(String label, String packageName, Drawable icon, boolean isSystem,
                 boolean isCamera, boolean isMusic, boolean isEmergency, boolean isClock) {
            this.label = label == null ? packageName : label;
            this.packageName = packageName;
            this.icon = icon;
            this.isSystem = isSystem;
            this.isCamera = isCamera;
            this.isMusic = isMusic;
            this.isEmergency = isEmergency;
            this.isClock = isClock;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureWindowBars();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(pageBackground());

        // Header view at top
        headerLayout = (LinearLayout) createHeaderView();
        root.addView(headerLayout);

        // Search Bar and Filter Chips
        root.addView(createSearchAndFilterView());

        // Scrollable list in middle
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        appListContainer.setPadding(dp(16), dp(8), dp(16), dp(16));

        scrollView.addView(appListContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom Loading Progress Container
        bottomLoadingContainer = createBottomLoadingView();
        root.addView(bottomLoadingContainer);

        // Apply System Window Insets to avoid notification bar & navigation bar overlapping
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    int topInset = insets.getSystemWindowInsetTop();
                    int bottomInset = insets.getSystemWindowInsetBottom();
                    headerLayout.setPadding(dp(16), topInset + dp(12), dp(16), dp(12));
                    bottomLoadingContainer.setPadding(dp(16), dp(8), dp(16), bottomInset + dp(8));
                    return insets;
                }
            });
        }

        setContentView(root);

        blacklistedPackages = OverlayPrefs.customBlacklistPackages(this);
        loadApplicationsAsync();
    }

    private void configureWindowBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.rgb(235, 245, 249));
            window.setNavigationBarColor(Color.rgb(224, 238, 244));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = window.getDecorView();
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
    }

    private View createHeaderView() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        header.setBackground(solidDrawable(Color.WHITE, 0, Color.argb(30, 0, 0, 0), dp(1)));

        TextView backBtn = new TextView(this);
        backBtn.setText("←");
        backBtn.setTextSize(22f);
        backBtn.setTextColor(COLOR_NAVY);
        backBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        backBtn.setPadding(0, 0, dp(16), 0);
        backBtn.setClickable(true);
        backBtn.setFocusable(true);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(backBtn);

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);

        // Top title: "Searching app" (top-aligned, bold)
        TextView title = new TextView(this);
        title.setText("Searching app");
        title.setTextColor(COLOR_NAVY);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleStack.addView(title);

        countTextView = new TextView(this);
        countTextView.setText("Discovering installed applications...");
        countTextView.setTextColor(COLOR_MUTED);
        countTextView.setTextSize(13f);
        titleStack.addView(countTextView);

        header.addView(titleStack, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        return header;
    }

    private View createSearchAndFilterView() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(12), dp(16), dp(8));

        // Search Bar
        EditText searchEditText = new EditText(this);
        searchEditText.setHint("Search app name or package...");
        searchEditText.setHintTextColor(COLOR_MUTED);
        searchEditText.setTextColor(COLOR_TEXT);
        searchEditText.setTextSize(14f);
        searchEditText.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchEditText.setBackground(solidDrawable(Color.WHITE, dp(12), Color.argb(50, 0, 132, 142), dp(1)));
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                filterAndRenderAppList();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        container.addView(searchEditText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Category Chips
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, dp(10), 0, 0);

        btnCategoryAll = createCategoryButton("All Apps", CATEGORY_ALL);
        btnCategoryUser = createCategoryButton("User Apps", CATEGORY_USER);
        btnCategorySystem = createCategoryButton("System Apps", CATEGORY_SYSTEM);

        filterRow.addView(btnCategoryAll, new LinearLayout.LayoutParams(0, dp(34), 1f));
        filterRow.addView(btnCategoryUser, new LinearLayout.LayoutParams(0, dp(34), 1f));
        filterRow.addView(btnCategorySystem, new LinearLayout.LayoutParams(0, dp(34), 1f));

        container.addView(filterRow);

        updateCategoryButtonStyles();
        return container;
    }

    private LinearLayout createBottomLoadingView() {
        LinearLayout bottomLayout = new LinearLayout(this);
        bottomLayout.setOrientation(LinearLayout.VERTICAL);
        bottomLayout.setGravity(Gravity.CENTER);
        bottomLayout.setPadding(dp(16), dp(8), dp(16), dp(12));
        bottomLayout.setBackground(solidDrawable(Color.WHITE, 0, Color.argb(30, 0, 0, 0), dp(1)));

        // Horizontal Linear Progress Bar at Bottom
        bottomProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bottomProgressBar.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressParams.setMargins(0, 0, 0, dp(6));
        bottomLayout.addView(bottomProgressBar, progressParams);

        // Text below bottom loading bar: "Discover package name"
        bottomDiscoverTextView = new TextView(this);
        bottomDiscoverTextView.setText("Discover package name");
        bottomDiscoverTextView.setTextColor(COLOR_ACCENT);
        bottomDiscoverTextView.setTextSize(13f);
        bottomDiscoverTextView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bottomDiscoverTextView.setGravity(Gravity.CENTER);
        bottomLayout.addView(bottomDiscoverTextView);

        return bottomLayout;
    }

    private Button createCategoryButton(String label, final int category) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(12f);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentCategory = category;
                updateCategoryButtonStyles();
                filterAndRenderAppList();
            }
        });
        return btn;
    }

    private void updateCategoryButtonStyles() {
        styleCategoryButton(btnCategoryAll, currentCategory == CATEGORY_ALL);
        styleCategoryButton(btnCategoryUser, currentCategory == CATEGORY_USER);
        styleCategoryButton(btnCategorySystem, currentCategory == CATEGORY_SYSTEM);
    }

    private void styleCategoryButton(Button btn, boolean selected) {
        if (btn == null) return;
        if (selected) {
            btn.setTextColor(COLOR_ACCENT);
            btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            btn.setBackground(solidDrawable(COLOR_ACCENT_BG, dp(8), COLOR_ACCENT, dp(1)));
        } else {
            btn.setTextColor(COLOR_MUTED);
            btn.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            btn.setBackground(solidDrawable(Color.WHITE, dp(8), Color.argb(30, 0, 0, 0), dp(1)));
        }
    }

    private void detectCategoryIntentPackages(PackageManager pm) {
        knownCameraPackages.clear();
        knownMusicPackages.clear();

        // 1. Camera Intent Query
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> cameraApps = pm.queryIntentActivities(cameraIntent, 0);
        for (ResolveInfo info : cameraApps) {
            if (info.activityInfo != null) {
                knownCameraPackages.add(info.activityInfo.packageName.toLowerCase(Locale.ROOT));
            }
        }

        // 2. Music Intent Query
        Intent musicIntent = new Intent(Intent.ACTION_MAIN);
        musicIntent.addCategory(Intent.CATEGORY_APP_MUSIC);
        List<ResolveInfo> musicApps = pm.queryIntentActivities(musicIntent, 0);
        for (ResolveInfo info : musicApps) {
            if (info.activityInfo != null) {
                knownMusicPackages.add(info.activityInfo.packageName.toLowerCase(Locale.ROOT));
            }
        }
    }

    private boolean isCameraApp(String pkg) {
        if (pkg == null) return false;
        String val = pkg.toLowerCase(Locale.ROOT);
        return knownCameraPackages.contains(val)
                || val.contains("camera")
                || val.equals("com.sec.android.app.camera");
    }

    private boolean isMusicApp(String pkg) {
        if (pkg == null) return false;
        String val = pkg.toLowerCase(Locale.ROOT);
        return knownMusicPackages.contains(val)
                || val.contains("spotify")
                || val.contains("music")
                || val.contains("soundcloud")
                || val.contains("shazam")
                || val.contains("deezer")
                || val.contains("ytmusic")
                || val.contains("pandora")
                || val.contains("tidal")
                || val.contains("apple.music")
                || val.contains("musicplayer");
    }

    private boolean isEmergencyApp(String pkg) {
        if (pkg == null) return false;
        String val = pkg.toLowerCase(Locale.ROOT);
        return val.contains("emergency")
                || val.contains("cellbroadcast")
                || val.contains("sos")
                || val.contains("safetyassurance");
    }

    private boolean isClockApp(String pkg) {
        if (pkg == null) return false;
        String val = pkg.toLowerCase(Locale.ROOT);
        return val.equals("com.sec.android.app.clockpackage")
                || val.equals("com.google.android.deskclock")
                || val.equals("com.oneplus.deskclock")
                || val.equals("com.coloros.alarmclock")
                || val.equals("com.android.deskclock")
                || val.equals("com.vivo.alarmclock")
                || val.contains("droom.sleepifucan");
    }

    private void loadApplicationsAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                detectCategoryIntentPackages(pm);

                List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                final List<AppEntry> entries = new ArrayList<AppEntry>();
                String selfPackage = getPackageName();

                boolean isFirstAutoScan = !OverlayPrefs.isCategoryAutoBlacklistApplied(AppPickerActivity.this);
                Set<String> autoBlacklistedAdded = new HashSet<String>();

                for (ApplicationInfo appInfo : installed) {
                    if (selfPackage.equals(appInfo.packageName)) {
                        continue;
                    }
                    String label = pm.getApplicationLabel(appInfo).toString();
                    Drawable icon = pm.getApplicationIcon(appInfo);
                    boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    boolean isCamera = isCameraApp(appInfo.packageName);
                    boolean isMusic = isMusicApp(appInfo.packageName);
                    boolean isEmergency = isEmergencyApp(appInfo.packageName);
                    boolean isClock = isClockApp(appInfo.packageName);

                    // Automatic Blacklisting Rules: Camera, Music, Emergency, and Clock apps are auto-blacklisted
                    if (isCamera || isMusic || isEmergency || isClock) {
                        autoBlacklistedAdded.add(appInfo.packageName.toLowerCase(Locale.ROOT));
                    }

                    entries.add(new AppEntry(label, appInfo.packageName, icon, isSystem, isCamera, isMusic, isEmergency, isClock));
                }

                if (!autoBlacklistedAdded.isEmpty()) {
                    Set<String> current = OverlayPrefs.customBlacklistPackages(AppPickerActivity.this);
                    current.addAll(autoBlacklistedAdded);
                    OverlayPrefs.saveCustomBlacklistPackages(AppPickerActivity.this, current);
                    blacklistedPackages = current;
                }

                if (isFirstAutoScan) {
                    OverlayPrefs.markCategoryAutoBlacklistApplied(AppPickerActivity.this);
                }

                Collections.sort(entries, new Comparator<AppEntry>() {
                    @Override
                    public int compare(AppEntry o1, AppEntry o2) {
                        return o1.label.compareToIgnoreCase(o2.label);
                    }
                });

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        allApps.clear();
                        allApps.addAll(entries);
                        bottomProgressBar.setIndeterminate(false);
                        bottomProgressBar.setProgress(100);
                        bottomDiscoverTextView.setText("Scan complete • " + allApps.size() + " package names discovered");
                        filterAndRenderAppList();
                    }
                });
            }
        }).start();
    }

    private void filterAndRenderAppList() {
        appListContainer.removeAllViews();

        List<AppEntry> filtered = new ArrayList<AppEntry>();
        int userCount = 0;
        int systemCount = 0;

        for (AppEntry app : allApps) {
            if (app.isSystem) {
                systemCount++;
            } else {
                userCount++;
            }

            if (currentCategory == CATEGORY_USER && app.isSystem) continue;
            if (currentCategory == CATEGORY_SYSTEM && !app.isSystem) continue;

            if (!currentSearchQuery.isEmpty()) {
                boolean matchesLabel = app.label.toLowerCase(Locale.ROOT).contains(currentSearchQuery);
                boolean matchesPkg = app.packageName.toLowerCase(Locale.ROOT).contains(currentSearchQuery);
                if (!matchesLabel && !matchesPkg) {
                    continue;
                }
            }

            filtered.add(app);
        }

        countTextView.setText(filtered.size() + " apps listed (" + userCount + " user, " + systemCount + " system)");

        if (filtered.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No applications match your filter.");
            emptyText.setTextColor(COLOR_MUTED);
            emptyText.setTextSize(14f);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, dp(40), 0, dp(40));
            appListContainer.addView(emptyText);
            return;
        }

        for (final AppEntry app : filtered) {
            appListContainer.addView(createAppRowView(app));
        }
    }

    private View createAppRowView(final AppEntry app) {
        final boolean isBlacklisted = blacklistedPackages.contains(app.packageName.toLowerCase(Locale.ROOT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(solidDrawable(
                isBlacklisted ? COLOR_ACCENT_BG : Color.WHITE,
                dp(10),
                isBlacklisted ? COLOR_ACCENT : Color.argb(20, 0, 0, 0),
                dp(1)
        ));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        // App Icon
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(app.icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(iconView, iconParams);

        // App Label + Package & Badges
        LinearLayout textStack = new LinearLayout(this);
        textStack.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(this);
        labelView.setText(app.label);
        labelView.setTextColor(COLOR_TEXT);
        labelView.setTextSize(15f);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textStack.addView(labelView);

        String badgeText = "";
        if (app.isCamera) badgeText += " • Camera";
        if (app.isMusic) badgeText += " • Music";
        if (app.isEmergency) badgeText += " • Emergency";
        if (app.isClock) badgeText += " • Clock";
        if (app.isSystem) badgeText += " • System";

        TextView pkgView = new TextView(this);
        pkgView.setText(app.packageName + badgeText);
        pkgView.setTextColor(COLOR_MUTED);
        pkgView.setTextSize(12f);
        pkgView.setTypeface(Typeface.MONOSPACE);
        textStack.addView(pkgView);

        row.addView(textStack, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Selection CheckBox
        final CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(isBlacklisted);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        row.addView(checkBox);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nextState = !blacklistedPackages.contains(app.packageName.toLowerCase(Locale.ROOT));
                if (nextState) {
                    OverlayPrefs.addCustomBlacklistPackage(AppPickerActivity.this, app.packageName);
                    blacklistedPackages.add(app.packageName.toLowerCase(Locale.ROOT));
                    Toast.makeText(AppPickerActivity.this, "Blacklisted " + app.label, Toast.LENGTH_SHORT).show();
                } else {
                    OverlayPrefs.removeCustomBlacklistPackage(AppPickerActivity.this, app.packageName);
                    blacklistedPackages.remove(app.packageName.toLowerCase(Locale.ROOT));
                    Toast.makeText(AppPickerActivity.this, "Removed " + app.label, Toast.LENGTH_SHORT).show();
                }
                checkBox.setChecked(nextState);
                row.setBackground(solidDrawable(
                        nextState ? COLOR_ACCENT_BG : Color.WHITE,
                        dp(10),
                        nextState ? COLOR_ACCENT : Color.argb(20, 0, 0, 0),
                        dp(1)
                ));
            }
        });

        return row;
    }

    private GradientDrawable pageBackground() {
        return solidDrawable(Color.rgb(235, 245, 249), 0, Color.TRANSPARENT, 0);
    }

    private GradientDrawable solidDrawable(int fillColor, int radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        if (radiusPx > 0) {
            drawable.setCornerRadius(radiusPx);
        }
        if (strokeWidthPx > 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
        return drawable;
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
