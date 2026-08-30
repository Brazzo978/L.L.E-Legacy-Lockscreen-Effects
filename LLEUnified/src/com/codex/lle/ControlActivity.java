package com.codex.lle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
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
import android.hardware.display.DisplayManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.text.InputType;
import android.view.Gravity;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import java.util.Collections;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

public class ControlActivity extends Activity {
    private static final float COMPACT_EFFECT_SWITCH_SCALE = 0.765f;
    // Hidden framework constant intentionally used by value: getDisplays(String) is public
    // and this category is the only API that also returns the inactive Fold panel.
    private static final String DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED =
            "android.hardware.display.category.ALL_INCLUDING_DISABLED";
    private static final String STATE_SELECTED_TAB = "selected_tab";
    private static final String STATE_PENDING_IMPORTED_PROFILE =
            "pending_imported_background_profile";
    private static final String STATE_PENDING_IMPORTED_EFFECT =
            "pending_imported_background_effect";
    private static final String STATE_PENDING_IMPORTED_WIDTH =
            "pending_imported_background_width";
    private static final String STATE_PENDING_IMPORTED_HEIGHT =
            "pending_imported_background_height";
    private static final String STATE_PENDING_LOCK_WALLPAPER_PREVIEW =
            "pending_lock_wallpaper_preview";
    private static final int REQUEST_IMPORTED_EFFECT_BACKGROUND = 4917;
    private static final int REQUEST_SETUP_WIZARD = 4918;
    private static final int REQUEST_IMPORTED_EFFECT_BACKGROUND_CROP = 4919;
    private static final int REQUEST_LOCK_WALLPAPER_ACCESS = 4920;
    private static final int REQUEST_READ_WALLPAPER_STORAGE = 4921;
    private static final int REQUEST_DOODLE_POSITION = 4922;
    private static final int TAB_LOCKSCREEN_EFFECT = 0;
    private static final int TAB_CHARGING_DOODLE = 1;
    private static final String PROJECT_GITHUB_URL =
            "https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects";
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
    private static final String[] RIPPLE_INK_PALETTE_NAMES = {
            "pink", "amber", "green", "electric blue", "navy", "violet", "brown", "cyan"
    };
    private static final int TAB_SWIPE_MIN_DISTANCE_DP = 72;
    private static final int TAB_DRAG_START_DISTANCE_DP = 12;
    private static final long TAB_ANIMATION_DURATION_MS = 270L;
    private static final float TAB_SWIPE_AXIS_RATIO = 1.35f;
    private static final float TAB_DRAG_AXIS_RATIO = 1.18f;
    private static final long EFFECT_SELECTION_APPLY_DELAY_MS = 2000L;

    /**
     * Preview one fully developed stock Samsung ink layer over white.  At w=1,
     * the palette target is c / (c + (1.5 - c)), so each raw component is c/1.5.
     */
    private static int rippleInkPreviewColor(int selector) {
        return Color.rgb(
                rippleInkPreviewComponent(selector, 0),
                rippleInkPreviewComponent(selector, 1),
                rippleInkPreviewComponent(selector, 2));
    }

    private static int rippleInkPreviewComponent(int selector, int channel) {
        float component = RippleInkPortEngine.paletteComponent(selector, channel);
        float rendered = component / (component + (1.5f - component));
        return Math.max(0, Math.min(255, Math.round(rendered * 255f)));
    }

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
    private int pendingImportedBackgroundEffect = -1;
    private int pendingImportedBackgroundWidth;
    private int pendingImportedBackgroundHeight;
    private String pendingImportedBackgroundProfile = "";
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
    private boolean doodleAdvancedExpanded;
    private boolean doodleDebugExpanded;
    private boolean lockscreenDebugExpanded;
    private boolean rendererWallpaperExpanded;
    private boolean touchBoxExpanded;
    private boolean randomPoolEditMode;
    private TextView randomPoolSummaryView;
    private boolean pendingLockWallpaperPreview;
    private boolean loadingLockWallpaperPreview;
    private final HashSet<String> expandedTimingSections = new HashSet<String>();
    private final HashMap<Integer, ArrayList<Switch>> highFrameRateSwitches =
            new HashMap<Integer, ArrayList<Switch>>();
    private boolean syncingHighFrameRateSwitches;
    private Typeface appFontRegular;
    private Typeface appFontBold;
    private PopupWindow effectPreviewPopup;
    private Bitmap effectPreviewPopupBitmap;
    private MediaPlayer effectPreviewMediaPlayer;
    private Surface effectPreviewVideoSurface;
    private int effectPreviewVideoGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("LLE64", "native baseline abi=" + Lle64Abi.verify());
        configureGraceWindow();
        prefs = OverlayPrefs.get(this);
        OverlayPrefs.migrateExperimentalNativeRefreshPrefsIfNeeded(this);
        OverlayPrefs.migrateLegacyTouchBoxIfNeeded(this);
        ensureTouchAreaEnabled();
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_SELECTED_TAB, TAB_LOCKSCREEN_EFFECT);
            pendingImportedBackgroundProfile = savedInstanceState.getString(
                    STATE_PENDING_IMPORTED_PROFILE, "");
            pendingImportedBackgroundEffect = savedInstanceState.getInt(
                    STATE_PENDING_IMPORTED_EFFECT, -1);
            pendingImportedBackgroundWidth = savedInstanceState.getInt(
                    STATE_PENDING_IMPORTED_WIDTH, 0);
            pendingImportedBackgroundHeight = savedInstanceState.getInt(
                    STATE_PENDING_IMPORTED_HEIGHT, 0);
            pendingLockWallpaperPreview = savedInstanceState.getBoolean(
                    STATE_PENDING_LOCK_WALLPAPER_PREVIEW, false);
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
        if (savedInstanceState == null && SetupWizardActivity.shouldLaunch(this)) {
            startActivityForResult(
                    SetupWizardActivity.createLaunchIntent(this, false),
                    REQUEST_SETUP_WIZARD);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SELECTED_TAB, selectedTab);
        outState.putString(STATE_PENDING_IMPORTED_PROFILE, pendingImportedBackgroundProfile);
        outState.putInt(STATE_PENDING_IMPORTED_EFFECT, pendingImportedBackgroundEffect);
        outState.putInt(STATE_PENDING_IMPORTED_WIDTH, pendingImportedBackgroundWidth);
        outState.putInt(STATE_PENDING_IMPORTED_HEIGHT, pendingImportedBackgroundHeight);
        outState.putBoolean(STATE_PENDING_LOCK_WALLPAPER_PREVIEW,
                pendingLockWallpaperPreview);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
        updateTouchBoxSummary();
        updateEffectProfilerSummary();
        if (pendingLockWallpaperPreview
                && LockscreenWallpaperProbe.hasReadAccess(this)) {
            pendingLockWallpaperPreview = false;
            showCurrentLockscreenWallpaper();
        }
        if (selectedTab == TAB_LOCKSCREEN_EFFECT && tabContent != null) {
            showTab(TAB_LOCKSCREEN_EFFECT, false, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOCK_WALLPAPER_ACCESS) {
            if (pendingLockWallpaperPreview
                    && LockscreenWallpaperProbe.hasReadAccess(this)) {
                pendingLockWallpaperPreview = false;
                showCurrentLockscreenWallpaper();
            }
            return;
        }
        if (requestCode == REQUEST_SETUP_WIZARD) {
            showTab(TAB_LOCKSCREEN_EFFECT, false, 0);
            return;
        }
        if (requestCode == REQUEST_DOODLE_POSITION) {
            showTab(TAB_CHARGING_DOODLE, false, 0);
            return;
        }
        if (requestCode == REQUEST_IMPORTED_EFFECT_BACKGROUND_CROP) {
            if (resultCode == RESULT_OK) {
                SetupWizardActivity.rememberWallpaperMode(
                        this, SetupWizardActivity.MODE_CACHE_ONLY);
                showTab(TAB_LOCKSCREEN_EFFECT, false, 0);
            }
            return;
        }
        if (requestCode != REQUEST_IMPORTED_EFFECT_BACKGROUND) {
            return;
        }
        final int effect = pendingImportedBackgroundEffect;
        final String profile = FoldDisplayTarget.normalizeProfile(
                pendingImportedBackgroundProfile);
        final int targetWidth = pendingImportedBackgroundWidth;
        final int targetHeight = pendingImportedBackgroundHeight;
        pendingImportedBackgroundEffect = -1;
        pendingImportedBackgroundProfile = "";
        pendingImportedBackgroundWidth = 0;
        pendingImportedBackgroundHeight = 0;
        final Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null || effect < 0 || targetWidth <= 0 || targetHeight <= 0) {
            return;
        }
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Throwable ignored) {
        }
        Intent crop = new Intent(this, WallpaperCropActivity.class);
        crop.setData(uri);
        crop.putExtra(WallpaperCropActivity.EXTRA_SOURCE_URI, uri.toString());
        crop.putExtra(WallpaperCropActivity.EXTRA_MODE,
                WallpaperCropActivity.MODE_CACHE_ONLY);
        crop.putExtra(WallpaperCropActivity.EXTRA_PROFILE, profile);
        crop.putExtra(WallpaperCropActivity.EXTRA_EFFECT, effect);
        crop.putExtra(WallpaperCropActivity.EXTRA_TARGET_WIDTH, targetWidth);
        crop.putExtra(WallpaperCropActivity.EXTRA_TARGET_HEIGHT, targetHeight);
        crop.putExtra(WallpaperCropActivity.EXTRA_REQUIRE_PRECISE_ACK, true);
        crop.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(crop, REQUEST_IMPORTED_EFFECT_BACKGROUND_CROP);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Wallpaper editor is unavailable",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_READ_WALLPAPER_STORAGE) {
            return;
        }
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingLockWallpaperPreview = false;
            showCurrentLockscreenWallpaper();
        } else {
            pendingLockWallpaperPreview = false;
            Toast.makeText(this, "Wallpaper read access was not granted",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
        uiHandler.removeCallbacks(applyPendingUnlockEffectRunnable);
        persistPendingUnlockEffect(false);
        hideEffectPreviewBubble();
        super.onStop();
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

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        titleStack.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("L.L.E");
        title.setTextColor(COLOR_GRACE_NAVY);
        title.setTextSize(29f);
        title.setSingleLine(true);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            title.setLetterSpacing(0.10f);
        }
        titleRow.addView(title);

        TextView version = new TextView(this);
        version.setText("v" + appVersionName());
        version.setTextColor(Color.rgb(89, 104, 137));
        version.setTextSize(10f);
        version.setSingleLine(true);
        version.setPadding(dp(7), 0, 0, dp(5));
        titleRow.addView(version);

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

    private String appVersionName() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            return versionName == null || versionName.trim().isEmpty()
                    ? "unknown" : versionName;
        } catch (PackageManager.NameNotFoundException error) {
            Log.w("LLEControl", "Unable to resolve app version", error);
            return "unknown";
        }
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
            page.addView(infoFooter());
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
                    SeekBar.class)
                    || isPointInsideViewType(
                            getWindow().getDecorView(),
                            tabSwipeDownX,
                            tabSwipeDownY,
                            Switch.class);
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

    private View infoFooter() {
        TextView footer = new TextView(this);
        footer.setText("GitHub  \u00b7  Made with love by Brazzo97 and Codex \u2661");
        footer.setTextColor(COLOR_MUTED);
        footer.setTextSize(12f);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(18), dp(8), dp(8));
        footer.setClickable(true);
        footer.setFocusable(true);
        footer.setContentDescription(
                "Open GitHub. Made with love by Brazzo97 and Codex. Heart.");
        footer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openProjectGitHub();
            }
        });
        return footer;
    }

    private void openProjectGitHub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_GITHUB_URL)));
        } catch (RuntimeException error) {
            Toast.makeText(this, "No browser is available", Toast.LENGTH_SHORT).show();
        }
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

        LinearLayout doodleLockSoundTiming = verticalGroup();
        doodleLockSoundTiming.addView(timeWindowControl(
                "Doodle lock sound active hours",
                OverlayPrefs.DOODLE_LOCK_SOUND_TIME_ENABLED,
                OverlayPrefs.DOODLE_LOCK_SOUND_TIME_START,
                OverlayPrefs.DOODLE_LOCK_SOUND_TIME_END));

        LinearLayout doodleExtras = verticalGroup();
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
        root.addView(seasonalEffectsCard());
        if (FoldDisplayTarget.usesFoldProfiles(this)) {
            root.addView(foldPanelRoutingControls());
        }
        root.addView(positionControls());
        root.addView(doodleAodControls());
        root.addView(doodleAdvancedMenu());
        root.addView(doodleDebugMenu());
        return root;
    }

    private View setupWizardControls() {
        LinearLayout section = verticalGroup();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        section.setLayoutParams(params);
        styleInsetPanel(section);
        section.addView(sectionTitle("Setup & permissions"));
        String mode = SetupWizardActivity.selectedWallpaperMode(this);
        String source = SetupWizardActivity.MODE_SET_LOCK_AND_CACHE.equals(mode)
                ? "user wallpaper (lockscreen + fixed cache, Beta)"
                : SetupWizardActivity.MODE_CACHE_ONLY.equals(mode)
                ? "current/imported exact wallpaper (Beta)" : "automatic screenshot";
        section.addView(infoText("Accessibility: "
                + (isChargingAccessibilityEnabled() ? "enabled" : "not enabled")
                + ". " + batteryOptimizationStatus()
                + " Background source: " + source + "."));
        section.addView(outlineButton("Run setup wizard", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(SetupWizardActivity.createLaunchIntent(
                        ControlActivity.this, true), REQUEST_SETUP_WIZARD);
            }
        }));
        section.addView(outlineButton("Change background source", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(SetupWizardActivity.createWallpaperLaunchIntent(
                        ControlActivity.this), REQUEST_SETUP_WIZARD);
            }
        }));
        section.addView(outlineButton("Show lockscreen wallpaper", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestLockscreenWallpaperPreview();
            }
        }));
        section.addView(outlineButton("Show lockscreen cache", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEffectBackgroundScreenshot();
            }
        }));
        section.addView(outlineButton("Show Last screen cache", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLgLastScreenCache();
            }
        }));
        if (EffectAvailability.is64BitProcess()) {
            section.addView(outlineButton("Create debug report", new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    createAndShareDebugReport(view, false);
                }
            }));
            section.addView(infoText("Creates a text-only support report and opens the "
                    + "share sheet. Wallpapers and images are never included."));
            section.addView(outlineButton("Create advanced log (unredacted)",
                    new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    confirmAdvancedDebugReport(view);
                }
            }));
            TextView advancedLogWarning = infoText("DANGER: the advanced log is not "
                    + "privacy-filtered. It may expose notification/accessibility text, "
                    + "app names, filenames, paths and exact touch coordinates. Share it "
                    + "only with a trusted recipient.");
            advancedLogWarning.setTextColor(COLOR_ERROR);
            advancedLogWarning.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            section.addView(advancedLogWarning);
        }
        return section;
    }

    private void confirmAdvancedDebugReport(final View source) {
        new AlertDialog.Builder(this)
                .setTitle("Advanced log may expose personal data")
                .setMessage("This unredacted report can contain notification or "
                        + "accessibility text, installed/foreground app identifiers, "
                        + "filenames, paths, imported source references and exact touch "
                        + "coordinates. Do not post it publicly. Share it only with someone "
                        + "you trust.\n\nCreate the advanced log now?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("I understand - create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createAndShareDebugReport(source, true);
                    }
                })
                .show();
    }

    private void createAndShareDebugReport(final View source, final boolean advanced) {
        source.setEnabled(false);
        Toast.makeText(this, advanced
                        ? "Creating unredacted advanced log\u2026"
                        : "Creating debug report\u2026",
                Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                File report = null;
                Throwable failure = null;
                try {
                    report = advanced
                            ? DebugReport.createAdvanced(ControlActivity.this)
                            : DebugReport.create(ControlActivity.this);
                } catch (Throwable error) {
                    failure = error;
                    Log.e("LLEControl", "Debug report creation failed", error);
                }
                final File completedReport = report;
                final Throwable completedFailure = failure;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        source.setEnabled(true);
                        if (completedReport == null) {
                            String detail = completedFailure == null
                                    ? "unknown error" : completedFailure.getMessage();
                            Toast.makeText(ControlActivity.this,
                                    "Unable to create report: " + detail,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        shareDebugReport(completedReport, advanced);
                    }
                });
            }
        }, "LLE-debug-report").start();
    }

    private void shareDebugReport(File report, boolean advanced) {
        Uri uri = DebugReportProvider.uriFor(this, report);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT,
                "L.L.E " + appVersionName()
                        + (advanced ? " advanced unredacted log" : " debug report"));
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.setClipData(ClipData.newRawUri(
                advanced ? "L.L.E advanced unredacted log" : "L.L.E debug report", uri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(share, advanced
                    ? "Share only with a trusted recipient"
                    : "Share L.L.E debug report"));
        } catch (RuntimeException error) {
            Log.e("LLEControl", "Debug report share failed", error);
            Toast.makeText(this, "No app is available to share the report",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestLockscreenWallpaperPreview() {
        if (!LockscreenWallpaperProbe.isSupported()) {
            Toast.makeText(this, "Requires Android 7 or newer",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (LockscreenWallpaperProbe.hasReadAccess(this)) {
            showCurrentLockscreenWallpaper();
            return;
        }
        pendingLockWallpaperPreview = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent access = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(access, REQUEST_LOCK_WALLPAPER_ACCESS);
            } catch (RuntimeException firstError) {
                try {
                    startActivityForResult(
                            new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                            REQUEST_LOCK_WALLPAPER_ACCESS);
                } catch (RuntimeException secondError) {
                    pendingLockWallpaperPreview = false;
                    Toast.makeText(this, "All files access settings are unavailable",
                            Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_READ_WALLPAPER_STORAGE);
    }

    private void showCurrentLockscreenWallpaper() {
        if (loadingLockWallpaperPreview) {
            return;
        }
        loadingLockWallpaperPreview = true;
        Toast.makeText(this, "Reading current lockscreen wallpaper…",
                Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                LockscreenWallpaperProbe.Result loaded = null;
                Throwable failure = null;
                try {
                    loaded = LockscreenWallpaperProbe.read(ControlActivity.this);
                } catch (Throwable error) {
                    failure = error;
                    Log.w("LLEControl", "Lockscreen wallpaper probe failed", error);
                }
                final LockscreenWallpaperProbe.Result result = loaded;
                final Throwable error = failure;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadingLockWallpaperPreview = false;
                        if (isFinishing() || isDestroyed()) {
                            recycleLockscreenWallpaperResult(result);
                            return;
                        }
                        if (result == null || result.bitmap == null) {
                            String message = error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "The current lockscreen wallpaper is unavailable";
                            Toast.makeText(ControlActivity.this, message,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        showLockscreenWallpaperDialog(result);
                    }
                });
            }
        }, "LLE-lock-wallpaper-probe").start();
    }

    private void showLockscreenWallpaperDialog(
            final LockscreenWallpaperProbe.Result result) {
        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackground(pageBackground());
        root.addView(sectionTitle("Current lockscreen wallpaper"));
        root.addView(infoText("BETA probe | " + result.sourceLabel()
                + " | original " + result.originalWidth + " x " + result.originalHeight
                + " | preview " + result.bitmap.getWidth() + " x "
                + result.bitmap.getHeight()));

        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(result.bitmap);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
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
                recycleLockscreenWallpaperResult(result);
            }
        });
        dialog.show();
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void recycleLockscreenWallpaperResult(LockscreenWallpaperProbe.Result result) {
        if (result != null && result.bitmap != null && !result.bitmap.isRecycled()) {
            result.bitmap.recycle();
        }
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
        section.addView(sectionTitle("Seasonal"));
        section.addView(effectPreviewHint());
        int currentSeason = prefs.getInt(OverlayPrefs.SEASON_MODE, SeasonalDoodleView.SEASON_AUTO);
        section.addView(seasonalEffectOption(
                "Seasonal",
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

    private View doodleAodControls() {
        LinearLayout section = verticalGroup();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(sectionTitle("Always On Display"));
        section.addView(toggle("Keep doodle visible on AOD",
                OverlayPrefs.DOODLE_AOD_ENABLED, false));
        section.addView(infoText("Shows a frozen, dimmed doodle while charging on Always On "
                + "Display. Animation resumes when the screen wakes."));
        return section;
    }

    private View doodleAdvancedMenu() {
        LinearLayout section = verticalGroup();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        section.setLayoutParams(params);
        styleCard(section);
        section.addView(collapsibleHeader("Advanced settings", doodleAdvancedExpanded,
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doodleAdvancedExpanded = !doodleAdvancedExpanded;
                showTab(selectedTab);
            }
        }));
        if (doodleAdvancedExpanded) {
            section.addView(doodleAdvancedControls());
        }
        return section;
    }

    private View doodleAdvancedControls() {
        LinearLayout section = verticalGroup();
        styleInsetPanel(section);
        section.addView(percentPreferenceControl("AOD brightness",
                OverlayPrefs.DOODLE_AOD_BRIGHTNESS_PERCENT,
                OverlayPrefs.DOODLE_AOD_BRIGHTNESS_DEFAULT_PERCENT));
        section.addView(percentPreferenceControl("AOD opacity",
                OverlayPrefs.DOODLE_AOD_OPACITY_PERCENT,
                OverlayPrefs.DOODLE_AOD_OPACITY_DEFAULT_PERCENT));
        section.addView(percentPreferenceControl("Lockscreen opacity",
                OverlayPrefs.DOODLE_OPACITY_PERCENT,
                OverlayPrefs.DOODLE_OPACITY_DEFAULT_PERCENT));
        section.addView(infoText("AOD brightness changes light output without replacing opacity. "
                + "Lockscreen opacity applies whenever the doodle is outside AOD."));
        return section;
    }

    private View percentPreferenceControl(final String label, final String key,
            int defaultValue) {
        LinearLayout row = verticalGroup();
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setBackground(controlRowBackground(false));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(rowParams);

        final TextView value = new TextView(this);
        value.setTextColor(COLOR_TEXT);
        value.setTextSize(15f);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setIncludeFontPadding(false);

        int initial = Math.max(0, Math.min(100, prefs.getInt(key, defaultValue)));
        value.setText(label + ": " + initial + "%");
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(initial);
        slider.setContentDescription(label);
        tintSeekBar(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = Math.max(0, Math.min(100, progress));
                value.setText(label + ": " + percent + "%");
                if (fromUser) {
                    prefs.edit().putInt(key, percent).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt(key, seekBar.getProgress()).apply();
            }
        });
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
        sliderParams.setMargins(0, dp(2), 0, 0);
        row.addView(slider, sliderParams);
        return row;
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
        highFrameRateSwitches.clear();
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
        View backgroundWarning = missingColormapWarning(current);
        if (backgroundWarning != null) {
            root.addView(backgroundWarning);
        }
        root.addView(controls);

        LinearLayout effects = verticalGroup();
        LinearLayout.LayoutParams effectsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        effectsParams.setMargins(0, 0, 0, dp(12));
        effects.setLayoutParams(effectsParams);
        styleCard(effects);
        effects.addView(sectionTitle("Effects"));
        if (OverlayPrefs.testerNoColormapModeEnabled(this)) {
            TextView lightweightMode = infoText(
                    "LIGHTWEIGHT TESTER MODE — lockscreen capture and colormap loading are "
                            + "disabled. Grey effects require a colormap; Mass Tension is the "
                            + "automatic safety fallback.");
            lightweightMode.setTextColor(COLOR_ACCENT_DEEP);
            lightweightMode.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            effects.addView(lightweightMode);
        }
        effects.addView(effectPreviewHint());
        effects.addView(randomEffectOption());
        effects.addView(sectionLabel("Samsung"));
        addEffectOptionIfAvailable(effects,
                "S3 None",
                "A clean white circle expands from your touch as the padlock flips open.",
                OverlayPrefs.EFFECT_S3_NONE,
                current);
        addEffectOptionIfAvailable(effects,
                "S3 Water Ripple",
                "Soft ripples flowing from your touch.",
                OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE,
                current);
        addEffectOptionIfAvailable(effects,
                "N2 Ink in Water",
                "Fluffy ink clouds blooming in water.",
                OverlayPrefs.EFFECT_N4_INK_IN_WATER,
                current);
        if (EffectAvailability.isAvailable(this, OverlayPrefs.EFFECT_S4_LENS_FLARE)) {
            effects.addView(lensFlareEffectOption(current));
        }
        if (EffectAvailability.isAvailable(this, OverlayPrefs.EFFECT_RIPPLE_INK)) {
            effects.addView(rippleInkEffectOption(current));
        }
        addEffectOptionIfAvailable(effects,
                "N3 Watercolor",
                "Watery paint spreading under your touch.",
                OverlayPrefs.EFFECT_WATERCOLOUR,
                current);
        addEffectOptionIfAvailable(effects,
                "S5 Brilliant Ring",
                "Thin sparkling rings expanding outward.",
                OverlayPrefs.EFFECT_BRILLIANT_RING,
                current);
        addEffectOptionIfAvailable(effects,
                "S5 Popping Colours",
                "Colorful particles burst and scatter from your touch.",
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                current);
        addEffectOptionIfAvailable(effects,
                "S5 Stone Skipping",
                "Skipping ripples crossing the wallpaper.",
                OverlayPrefs.EFFECT_STONE_SKIPPING,
                current);
        addEffectOptionIfAvailable(effects,
                "Tab S Blind",
                "Glowing blinds opening under your touch.",
                OverlayPrefs.EFFECT_TABS_BLIND,
                current);
        addEffectOptionIfAvailable(effects,
                "Tab S Brilliant Cut",
                "A crystal grid sparkling beneath touch.",
                OverlayPrefs.EFFECT_BRILLIANT_CUT,
                current);
        if (EffectAvailability.isAvailable(OverlayPrefs.EFFECT_S4_ABSTRACT_TILES)) {
            if (EffectAvailability.is64BitProcess()) {
                boolean currentLineMode = pendingUnlockEffect
                        == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                        && pendingAbstractTilesLineMode >= 0
                        ? pendingAbstractTilesLineMode == 1
                        : OverlayPrefs.abstractTilesLineEnabled(this);
                effects.addView(abstractTilesEffectOption(
                        "N4 Abstract Tiles",
                        "Geometric tiles scattering across the wallpaper.",
                        current,
                        currentLineMode));
            } else {
                effects.addView(effectOption(
                        "N4 Abstract Tiles",
                        "Geometric tiles scattering from touch.",
                        OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                        current));
            }
        }
        addEffectOptionIfAvailable(effects,
                "N4 Geometric Mosaic",
                "Mosaic fragments shifting and breaking apart.",
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC,
                current);
        addEffectOptionIfAvailable(effects,
                "T2 Mass Tension",
                "An elastic ring stretching and snapping back under touch.",
                OverlayPrefs.EFFECT_MASS_TENSION,
                current);
        addEffectOptionIfAvailable(effects,
                EffectAvailability.hasLegacyVendorEffects()
                        ? "S6 Water Droplet (Samsung legacy)"
                        : "S6 Water Droplet",
                "Refracted water droplets flowing across the wallpaper.",
                OverlayPrefs.EFFECT_S6_WATER_DROPLET,
                current);
        addEffectOptionIfAvailable(effects,
                EffectAvailability.hasLegacyVendorEffects()
                        ? "S6 Water Droplet (LLE renderer)"
                        : "S6 Water Droplet",
                "Refracted water droplets flowing across the wallpaper.",
                OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED,
                current);
        if (EffectAvailability.hasLegacyVendorEffects()) {
            addEffectOptionIfAvailable(effects,
                    "N5 Colored Droplet (Samsung legacy)",
                    "Colorful liquid droplets rolling across screen.",
                    OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                    current);
            addEffectOptionIfAvailable(effects,
                    "N5 Colored Droplet + Gyro (Samsung legacy)",
                    "Liquid droplets flowing with phone movement.",
                    OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                    current);
        }
        if (EffectAvailability.isAvailable(OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP)
                && EffectAvailability.isAvailable(
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP)) {
            effects.addView(colourDropletEffectOption(current));
        }
        addEffectOptionIfAvailable(effects,
                EffectAvailability.hasLegacyVendorEffects()
                        ? "N5 Sparkling Bubbles (Samsung legacy)"
                        : "N5 Sparkling Bubbles",
                "Glowing bubbles sparkling across the wallpaper.",
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                current);
        addEffectOptionIfAvailable(effects,
                EffectAvailability.hasLegacyVendorEffects()
                        ? "N5 Sparkling Bubbles (LLE renderer)"
                        : "N5 Sparkling Bubbles",
                "Glowing bubbles sparkling across the wallpaper.",
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP,
                current);
        effects.addView(sectionLabel("Good Lock"));
        addEffectOptionIfAvailable(effects,
                "Good Lock Popping Color",
                "Bright color particles drift and scatter from your touch.",
                OverlayPrefs.EFFECT_GOOD_LOCK_POPPING,
                current);
        addEffectOptionIfAvailable(effects,
                "Good Lock Rectangle Traveller",
                "Bright rectangles race outward from your touch.",
                OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE,
                current);
        addEffectOptionIfAvailable(effects,
                "Good Lock Bouncing Color",
                "Color particles spring and bounce away from your touch.",
                OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING,
                current);
        effects.addView(sectionLabel("LG effects"));
        addEffectOptionIfAvailable(effects,
                "G1 White Hole",
                "A glittering white ring expands from your touch into a luminous portal.",
                OverlayPrefs.EFFECT_LG_G1_WHITE_HOLE,
                current);
        addEffectOptionIfAvailable(effects,
                "G2 Soda",
                "Bubbles and sparkling particles swirl around an expanding circular window.",
                OverlayPrefs.EFFECT_LG_SODA,
                current);
        addEffectOptionIfAvailable(effects,
                "G1 Dewdrop",
                "A glossy liquid droplet bends the image beneath your touch before spreading outward.",
                OverlayPrefs.EFFECT_LG_G1_DEWDROP,
                current);
        addEffectOptionIfAvailable(effects,
                "G2 Particle",
                "A halo of bright particles gathers at your touch and sweeps outward with the drag.",
                OverlayPrefs.EFFECT_LG_G2_PARTICLE,
                current);
        addEffectOptionIfAvailable(effects,
                "G2 Light Particle",
                "Soft bokeh lights and glittering particles bloom around your touch.",
                OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE,
                current);
        addEffectOptionIfAvailable(effects,
                "G2 Pixelate",
                "Triangular pixels grow larger with your drag, breaking the image into a shifting mosaic.",
                OverlayPrefs.EFFECT_LG_G2_PIXELATE,
                current);
        addEffectOptionIfAvailable(effects,
                "G2 Crystal",
                "A faceted crystal spins and refracts the image beneath your touch.",
                OverlayPrefs.EFFECT_LG_G2_CRYSTAL,
                current);
        effects.addView(sectionLabel("Sony"));
        addEffectOptionIfAvailable(effects,
                "Xperia Z1 Blinds",
                "Horizontal strips peel open from your touch to reveal the screen beneath.",
                OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS,
                current);
        addEffectOptionIfAvailable(effects,
                "Revolving Glass",
                "A luminous glass tile lifts, tilts and spins with your swipe before shrinking away.",
                OverlayPrefs.EFFECT_REVOLVING_GLASS,
                current);
        effects.addView(sectionLabel("Seasonal"));
        addEffectOptionIfAvailable(effects,
                "Seasonal",
                "Particles matching the current season.",
                OverlayPrefs.EFFECT_SEASONAL_AUTO,
                current);
        addEffectOptionIfAvailable(effects,
                "Seasonal Spring",
                "Pink blossoms drifting across screen.",
                OverlayPrefs.EFFECT_SEASONAL_SPRING,
                current);
        addEffectOptionIfAvailable(effects,
                "Seasonal Summer",
                "Warm golden sparks scattering from touch.",
                OverlayPrefs.EFFECT_SEASONAL_SUMMER,
                current);
        addEffectOptionIfAvailable(effects,
                "Seasonal Autumn",
                "Autumn leaves swirling and falling.",
                OverlayPrefs.EFFECT_SEASONAL_AUTUMN,
                current);
        addEffectOptionIfAvailable(effects,
                "Seasonal Winter",
                "Snowflakes sparkling around your touch.",
                OverlayPrefs.EFFECT_SEASONAL_WINTER,
                current);
        root.addView(effects);
        root.addView(infoFooter());
        if (effectUsesColormapCache(current)
                || OverlayPrefs.needsLgPreLockUnderlay(current)
                || (BuildFlavor.TESTER
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            root.addView(screenshotServiceControls(current));
        }
        return root;
    }

    private View missingColormapWarning(final int effect) {
        if (!effectUsesColormapCache(effect)) {
            return null;
        }
        final ArrayList<String> missingProfiles = new ArrayList<String>();
        for (String profile : FoldDisplayTarget.backgroundProfiles(this)) {
            addMissingAutomaticColormapProfile(missingProfiles, effect, profile);
        }
        if (missingProfiles.isEmpty()) {
            return null;
        }

        LinearLayout warning = verticalGroup();
        warning.setPadding(dp(15), dp(12), dp(15), dp(12));
        warning.setBackground(gradient(
                GradientDrawable.Orientation.TL_BR,
                new int[] {Color.rgb(255, 249, 235), Color.rgb(255, 241, 232)},
                dp(18),
                Color.rgb(232, 154, 77),
                dp(1)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            warning.setElevation(dp(4));
        }
        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        warningParams.setMargins(0, 0, 0, dp(12));
        warning.setLayoutParams(warningParams);

        TextView title = new TextView(this);
        title.setText("\u26a0  No colormap screenshot ready");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(4));
        warning.addView(title);

        String missing = joinProfileLabels(missingProfiles);
        TextView copy = infoText("Missing"
                + (missing.isEmpty() ? "." : " for " + missing + ".")
                + " Tap recapture, then lock \u2192 wait \u2192 unlock.");
        copy.setTextColor(Color.rgb(104, 76, 59));
        copy.setTextSize(12.5f);
        copy.setPadding(0, 0, 0, dp(6));
        warning.addView(copy);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button recapture = compactWarningButton("Force recapture now", true,
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyPendingUnlockEffect();
                OverlayPrefs.requestEffectBackgroundRefresh(ControlActivity.this);
                Toast.makeText(ControlActivity.this,
                        isChargingAccessibilityEnabled()
                                ? "Recapture armed \u2014 lock, wait, then unlock"
                                : "Recapture armed \u2014 enable Accessibility, then lock and unlock",
                        Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams recaptureParams = new LinearLayout.LayoutParams(
                0, dp(42), 1.35f);
        recaptureParams.setMargins(0, 0, dp(7), 0);
        actions.addView(recapture, recaptureParams);

        Button source = compactWarningButton("Change source", false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivityForResult(SetupWizardActivity.createWallpaperLaunchIntent(
                                ControlActivity.this), REQUEST_SETUP_WIZARD);
                    }
                });
        actions.addView(source, new LinearLayout.LayoutParams(0, dp(42), 0.8f));
        warning.addView(actions);
        return warning;
    }

    private Button compactWarningButton(String text, boolean filled,
            View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12.5f);
        button.setTextColor(filled ? Color.WHITE : Color.rgb(181, 89, 35));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(listener);
        button.setBackground(filled
                ? gradient(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[] {Color.rgb(216, 104, 43), Color.rgb(238, 151, 56)},
                        dp(14), Color.TRANSPARENT, 0)
                : solidDrawable(Color.argb(115, 255, 255, 255), dp(14),
                        Color.argb(150, 216, 104, 43), dp(1)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(filled ? dp(2) : 0f);
        }
        return button;
    }

    private void addMissingAutomaticColormapProfile(ArrayList<String> missingProfiles,
            int effect, String requestedProfile) {
        String profile = FoldDisplayTarget.normalizeProfile(requestedProfile);
        if (OverlayPrefs.importedEffectBackgroundEnabled(this, effect, profile)) {
            return;
        }
        File screenshot = colormapScreenshotFileForPreview(effect, profile);
        if (isReadableImageFile(screenshot)) {
            return;
        }
        missingProfiles.add(FoldDisplayTarget.PROFILE_SINGLE.equals(profile)
                ? "this display" : FoldDisplayTarget.profileLabel(profile));
    }

    private boolean isReadableImageFile(File file) {
        return Argb8888BitmapStore.isUsable(file);
    }

    private String joinProfileLabels(ArrayList<String> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return "";
        }
        if (profiles.size() == 1) {
            return profiles.get(0);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < profiles.size(); i++) {
            if (i > 0) {
                result.append(i == profiles.size() - 1 ? " and " : ", ");
            }
            result.append(profiles.get(i));
        }
        return result.toString();
    }

    private void addEffectOptionIfAvailable(LinearLayout effects, String title,
            String description, int effect, int current) {
        if (!EffectAvailability.isAvailable(this, effect)) {
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
        section.addView(collapsibleHeader("Wallpaper source", rendererWallpaperExpanded,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rendererWallpaperExpanded = !rendererWallpaperExpanded;
                        showTab(selectedTab, false, 0);
                    }
                }));
        section.addView(infoText(effectBackgroundStatus(currentEffect)));
        if (!rendererWallpaperExpanded) {
            return section;
        }
        if (OverlayPrefs.needsLgPreLockUnderlay(currentEffect)) {
            String effectName = OverlayPrefs.effectLabel(currentEffect);
            boolean secondary = OverlayPrefs.usesLgPreLockUnderlayAsSecondary(currentEffect);
            section.addView(infoText(secondary
                    ? effectName + " uses two independent images: Last screen remains fixed "
                            + "under the effect, while the lockscreen cache is mapped only to "
                            + "the rotating glass tile. Neither cache replaces the other."
                    : effectName + " uses Last screen: on a normal screen-off, L.L.E captures "
                            + "the final unlocked app/launcher frame and supplies that private "
                            + "buffer only to effects that request it. It is separate from, and "
                            + "never replaces, the lockscreen colormap used by other effects."));
            section.addView(sectionLabel(secondary
                    ? "LOCKSCREEN CACHE · ROTATING TILE"
                    : "LOCKSCREEN CACHE · OTHER EFFECTS"));
            section.addView(infoText(effectBackgroundProfileStatus(
                    currentEffect, FoldDisplayTarget.cacheProfileForContext(this))));
            section.addView(outlineButton("Force lockscreen cache recapture",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            applyPendingUnlockEffect();
                            OverlayPrefs.requestEffectBackgroundRefresh(ControlActivity.this);
                            Toast.makeText(ControlActivity.this,
                                    "Lockscreen cache recapture queued",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }));
            section.addView(outlineButton("View lockscreen cache",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showEffectBackgroundScreenshot();
                        }
                    }));
            addLgLastScreenCacheControls(section, currentEffect);
            addTesterUnderlayProbeControls(section);
            return section;
        }
        if (!effectUsesColormapCache(currentEffect)) {
            section.addView(infoText(
                    "The selected effect is intentionally colormap-free. No wallpaper image "
                            + "is captured or supplied to its renderer."));
            addTesterUnderlayProbeControls(section);
            return section;
        }
        final String activeProfile = FoldDisplayTarget.cacheProfileForContext(this);
        final String[] profiles = FoldDisplayTarget.backgroundProfiles(this);
        boolean multipleProfiles = profiles.length > 1;
        boolean directModeActive = false;
        boolean automaticModeActive = false;
        for (String profile : profiles) {
            boolean imported = OverlayPrefs.importedEffectBackgroundEnabled(
                    this, currentEffect, profile);
            directModeActive |= imported;
            automaticModeActive |= !imported;
        }

        if (directModeActive) {
            section.addView(sectionLabel("EXTRA / BETA - Direct wallpaper active"));
            section.addView(infoText(
                    "Beta feature. LLE sends a private, display-sized wallpaper directly "
                            + "to screenshot-driven effects. Some effect UI masks may still "
                            + "need refinement."));
            for (String profile : profiles) {
                addDirectWallpaperProfileControls(section, currentEffect, profile);
            }
            section.addView(outlineButton(multipleProfiles
                    ? "View direct wallpapers"
                    : "View direct wallpaper", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPendingUnlockEffect();
                    showEffectBackgroundScreenshot();
                }
            }));
        }

        if (automaticModeActive) {
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
            section.addView(outlineButton(multipleProfiles
                    ? "View automatic profile screenshots"
                    : "View colormap screenshot", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPendingUnlockEffect();
                    showEffectBackgroundScreenshot();
                }
            }));
            section.addView(sectionLabel("Automatic recapture"));
            section.addView(toggle("Auto recapture expired cache",
                    OverlayPrefs.EFFECT_BACKGROUND_AUTO_REFRESH_ENABLED, false));
            section.addView(effectBackgroundIntervalSelector());
            section.addView(toggle("Pause auto recapture 23-07",
                    OverlayPrefs.EFFECT_BACKGROUND_SKIP_NIGHT, true));
            section.addView(toggle("Wake lockscreen for hard recapture",
                    OverlayPrefs.EFFECT_BACKGROUND_FORCE_RECAPTURE, false));
            section.addView(infoText("These settings apply only to Automatic screenshot. "
                    + "Direct wallpaper sources remain fixed until you replace them."));
        }
        addTesterUnderlayProbeControls(section);
        return section;
    }

    private void addLgLastScreenCacheControls(LinearLayout section, final int effect) {
        final LgLastScreenCache.Target target = LgLastScreenCache.activeTarget(this);
        section.addView(sectionLabel("LAST SCREEN"));
        section.addView(outlineButton("View Last screen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLgLastScreenCache();
            }
        }));
        section.addView(outlineButton("Force wallpaper fallback", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmLgLastScreenFallback(effect, target);
            }
        }));
        section.addView(infoText(
                "Fallback is a one-time safety seed: it copies an exact-size wallpaper cache "
                        + "into Last screen. The next successful screen-off capture replaces "
                        + "it with the real last unlocked frame."));
    }

    private void showLgLastScreenCache() {
        final LgLastScreenCache.Target target = LgLastScreenCache.activeTarget(this);
        Argb8888BitmapStore.Info sourceInfo = LgLastScreenCache.inspect(target);
        if (sourceInfo == null) {
            Toast.makeText(this,
                    "No Last screen cache yet. Turn the screen off once while normally unlocked.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final Bitmap bitmap = decodePreviewBitmap(target.file);
        if (bitmap == null || bitmap.isRecycled()) {
            Toast.makeText(this, "Last screen cache unreadable", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackground(pageBackground());
        root.addView(sectionTitle("Last screen"));
        String source = LgLastScreenCache.isWallpaperFallback(this, target)
                ? "forced wallpaper fallback" : "last unlocked frame";
        root.addView(infoText(source + " | " + target.profile + " | "
                + sourceInfo.width + " x " + sourceInfo.height + " | "
                + Math.max(1L, target.file.length() / 1024L) + " KB"));

        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
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

    private void confirmLgLastScreenFallback(final int effect,
            final LgLastScreenCache.Target target) {
        File fallback = LgLastScreenCache.findWallpaperFallback(this, effect, target);
        if (fallback == null) {
            Toast.makeText(this,
                    "No exact-size wallpaper cache is available. Capture or import one first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Force wallpaper fallback?")
                .setMessage("This replaces the current Last screen image with the traditional "
                        + "wallpaper cache. It is only a safety fallback; the next successful "
                        + "screen-off capture will replace it with the real last screen.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Force fallback", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        forceLgLastScreenFallback(effect, target);
                    }
                })
                .show();
    }

    private void forceLgLastScreenFallback(final int effect,
            final LgLastScreenCache.Target target) {
        Toast.makeText(this, "Preparing Last screen fallback…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final LgLastScreenCache.FallbackResult result =
                        LgLastScreenCache.forceWallpaperFallback(
                                ControlActivity.this, effect, target);
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result.saved) {
                            ChargingAccessibilityService.reloadLgLastScreenCache();
                        }
                        Toast.makeText(ControlActivity.this, result.message,
                                result.saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                        if (!isFinishing() && !isDestroyed()) {
                            showTab(selectedTab, false, 0);
                        }
                    }
                });
            }
        }, "LLE-last-screen-fallback").start();
    }

    private void addTesterUnderlayProbeControls(LinearLayout section) {
        if (!BuildFlavor.TESTER
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return;
        }
        section.addView(sectionLabel("TESTER - LG underlay API probe"));
        section.addView(infoText(
                "Tests whether Android exposes the launcher or previous app as a separate "
                        + "accessibility window while locked. This does not replace or modify "
                        + "the current colormap."));
        section.addView(infoText("Status: "
                + ChargingAccessibilityService.testerUnderlayProbeStatus(this)));
        section.addView(outlineButton("Arm probe (30-second window)",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyPendingUnlockEffect();
                        boolean armed = ChargingAccessibilityService
                                .scheduleTesterUnderlayProbe(0L);
                        Toast.makeText(ControlActivity.this,
                                armed
                                        ? "Probe armed. Go Home, lock and wake to the lockscreen within 30 seconds."
                                        : "Accessibility service unavailable or API unsupported",
                                Toast.LENGTH_LONG).show();
                        if (armed) {
                            showTab(selectedTab, false, 0);
                        }
                    }
                }));
        section.addView(outlineButton("View last underlay probe",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showTesterUnderlayProbe();
                    }
                }));
    }

    private void addDirectWallpaperProfileControls(LinearLayout section, final int effect,
            final String requestedProfile) {
        final String profile = FoldDisplayTarget.normalizeProfile(requestedProfile);
        final int[] targetSize = effectBackgroundTargetSize(profile);
        final boolean imported = OverlayPrefs.importedEffectBackgroundEnabled(
                this, effect, profile);
        String profileLabel = FoldDisplayTarget.profileLabel(profile);
        String sourceLabel = imported ? "DIRECT active" : "AUTO screenshot";
        section.addView(infoText(profileLabel.toUpperCase(Locale.US)
                + "  |  " + targetSize[0] + " x " + targetSize[1]
                + "  |  " + sourceLabel));
        section.addView(outlineButton(
                "Choose wallpaper for " + profileLabel + " ("
                        + targetSize[0] + " x " + targetSize[1] + ")",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startImportedEffectBackgroundPicker(effect, profile);
                    }
                }));
        section.addView(outlineButton(imported
                ? "Use automatic screenshot on " + profileLabel
                : "Automatic screenshot active on " + profileLabel,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!OverlayPrefs.importedEffectBackgroundEnabled(
                                ControlActivity.this, effect, profile)) {
                            Toast.makeText(ControlActivity.this,
                                    "Automatic screenshot is already active for " + profile,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        OverlayPrefs.useAutomaticEffectBackgroundForAll(
                                ControlActivity.this, profile);
                        SetupWizardActivity.rememberWallpaperMode(
                                ControlActivity.this,
                                SetupWizardActivity.MODE_AUTOMATIC_SCREENSHOT);
                        OverlayPrefs.requestEffectBackgroundRefresh(ControlActivity.this);
                        Toast.makeText(ControlActivity.this,
                                "Automatic screenshot restored for " + profile
                                        + "; imported file kept privately",
                                Toast.LENGTH_LONG).show();
                        showTab(TAB_LOCKSCREEN_EFFECT, false, 0);
                    }
                }));
    }

    private int[] effectBackgroundTargetSize(String requestedProfile) {
        return FoldDisplayTarget.displaySizeForProfile(this, requestedProfile);
    }

    private void startImportedEffectBackgroundPicker(int effect, String requestedProfile) {
        if (!effectUsesColormapCache(effect)) {
            Toast.makeText(this,
                    "This effect does not use a screenshot-backed renderer",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final String profile = FoldDisplayTarget.normalizeProfile(requestedProfile);
        final int selectedEffect = effect;
        new AlertDialog.Builder(this)
                .setTitle("Are you sure?")
                .setMessage("Direct wallpaper colormaps are still beta. Automatic screenshot "
                        + "is the recommended and safest option.")
                .setNegativeButton("Use automatic screenshot",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                useAutomaticScreenshotForProfile(profile);
                            }
                        })
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showFinalDirectWallpaperConfirmation(selectedEffect, profile);
                    }
                })
                .show();
    }

    private void showFinalDirectWallpaperConfirmation(final int effect,
            final String profile) {
        new AlertDialog.Builder(this)
                .setTitle("Are you sure sure?")
                .setMessage("L.L.E will use a manually supplied fixed colormap for this "
                        + "display profile. Continue only if you understand how it differs "
                        + "from Automatic screenshot.")
                .setNegativeButton("Please use automatic screenshot if you don't know what "
                                + "you're doing",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                useAutomaticScreenshotForProfile(profile);
                            }
                        })
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openImportedEffectBackgroundPicker(effect, profile);
                    }
                })
                .show();
    }

    private void useAutomaticScreenshotForProfile(String profile) {
        OverlayPrefs.useAutomaticEffectBackgroundForAll(this, profile);
        SetupWizardActivity.rememberWallpaperMode(
                this, SetupWizardActivity.MODE_AUTOMATIC_SCREENSHOT);
        OverlayPrefs.requestEffectBackgroundRefresh(this);
        Toast.makeText(this,
                "Automatic screenshot active for " + profile,
                Toast.LENGTH_LONG).show();
        showTab(TAB_LOCKSCREEN_EFFECT, false, 0);
    }

    private void openImportedEffectBackgroundPicker(int effect, String profile) {
        int[] targetSize = effectBackgroundTargetSize(profile);
        pendingImportedBackgroundEffect = effect;
        pendingImportedBackgroundProfile = profile;
        pendingImportedBackgroundWidth = targetSize[0];
        pendingImportedBackgroundHeight = targetSize[1];
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(picker, REQUEST_IMPORTED_EFFECT_BACKGROUND);
        } catch (Throwable openDocumentFailure) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("image/*");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(
                        Intent.createChooser(fallback, "Choose renderer wallpaper"),
                        REQUEST_IMPORTED_EFFECT_BACKGROUND);
            } catch (Throwable fallbackFailure) {
                pendingImportedBackgroundEffect = -1;
                pendingImportedBackgroundProfile = "";
                pendingImportedBackgroundWidth = 0;
                pendingImportedBackgroundHeight = 0;
                Toast.makeText(this,
                        "No compatible image picker is available", Toast.LENGTH_LONG).show();
            }
        }
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
        persistPendingUnlockEffect(true);
    }

    private void persistPendingUnlockEffect(boolean refreshUi) {
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
        if (refreshUi) {
            showTab(TAB_LOCKSCREEN_EFFECT);
        }
    }

    private String effectBackgroundStatus(int effect) {
        if (OverlayPrefs.needsLgPreLockUnderlay(effect)) {
            LgLastScreenCache.Target target = LgLastScreenCache.activeTarget(this);
            Argb8888BitmapStore.Info info = LgLastScreenCache.inspect(target);
            String lastScreenStatus;
            if (info == null) {
                lastScreenStatus = "Last screen (" + target.profile
                        + "): empty. Unlock normally, leave "
                        + "the app or launcher you want visible, then turn the screen off once.";
            } else {
                long capturedAt = LgLastScreenCache.capturedAt(this, target);
                long ageMs = capturedAt <= 0L
                        ? 0L : Math.max(0L, System.currentTimeMillis() - capturedAt);
                String source = LgLastScreenCache.isWallpaperFallback(this, target)
                        ? "forced wallpaper fallback" : "captured last unlocked frame";
                lastScreenStatus = "Last screen (" + target.profile + "): ready, "
                        + info.width + " x " + info.height + ", age " + ageLabel(ageMs)
                        + ", source: " + source
                        + ". Dedicated cache; lockscreen colormap unchanged.";
            }
            if (OverlayPrefs.usesLgPreLockUnderlayAsSecondary(effect)) {
                return lastScreenStatus + "\nRotating tile: "
                        + effectBackgroundProfileStatus(
                                effect, FoldDisplayTarget.cacheProfileForContext(this));
            }
            return lastScreenStatus;
        }
        if (!effectUsesColormapCache(effect)) {
            return "Screenshot cache: not used by this effect.";
        }
        String profile = FoldDisplayTarget.cacheProfileForContext(this);
        String[] profiles = FoldDisplayTarget.backgroundProfiles(this);
        if (profiles.length > 1) {
            StringBuilder status = new StringBuilder();
            for (String candidate : profiles) {
                if (status.length() > 0) {
                    status.append('\n');
                }
                status.append(effectBackgroundProfileStatus(effect, candidate));
            }
            status.append("\nActive profile: ").append(profile);
            return status.toString();
        }
        return effectBackgroundProfileStatus(effect, profile);
    }

    private String effectBackgroundProfileStatus(int effect, String profile) {
        if (OverlayPrefs.importedEffectBackgroundEnabled(this, effect, profile)) {
            File imported = OverlayPrefs.importedEffectBackgroundFile(this, effect, profile);
            String label = OverlayPrefs.importedEffectBackgroundLabel(this, effect, profile);
            int width = OverlayPrefs.importedEffectBackgroundWidth(this, effect, profile);
            int height = OverlayPrefs.importedEffectBackgroundHeight(this, effect, profile);
            long importedAt = OverlayPrefs.importedEffectBackgroundAt(this, effect, profile);
            if (!ManualEffectBackground.isUsable(imported)) {
                return "EXTRA / Beta Imported (" + profile + "): selected but missing or "
                        + "unreadable. Automatic capture remains paused until you choose "
                        + "another image or reset to Auto.";
            }
            long ageMs = importedAt <= 0L
                    ? 0L : Math.max(0L, System.currentTimeMillis() - importedAt);
            return "EXTRA / Beta Imported (" + profile + "): " + label
                    + " | original " + width + " x " + height
                    + " | imported " + ageLabel(ageMs)
                    + ". Full-frame center crop; automatic capture paused.";
        }
        File file = colormapScreenshotFileForPreview(effect, profile);
        long capturedAt = OverlayPrefs.effectBackgroundLastCapturedAt(this, effect, profile);
        if (capturedAt <= 0L && file.exists()) {
            capturedAt = file.lastModified();
        }
        if (!file.exists() || file.length() <= 0L || capturedAt <= 0L) {
            return "AUTO screenshot cache (" + profile + "): empty.";
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - capturedAt);
        return "AUTO screenshot cache (" + profile + "): ready, age " + ageLabel(ageMs)
                + ". Expired cache stays active until a validated capture replaces it.";
    }

    private boolean effectUsesColormapCache(int effect) {
        if (OverlayPrefs.testerNoColormapModeEnabled(this)) {
            return false;
        }
        return effect == OverlayPrefs.EFFECT_S4_LENS_FLARE
                || effect == OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE
                || effect == OverlayPrefs.EFFECT_N4_INK_IN_WATER
                || effect == OverlayPrefs.EFFECT_S5_POPPING_COLOURS
                || effect == OverlayPrefs.EFFECT_TABS_BLIND
                || effect == OverlayPrefs.EFFECT_WATERCOLOUR
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                || effect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES
                || effect == OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET
                || effect == OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED
                || effect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES
                || effect == OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC
                || effect == OverlayPrefs.EFFECT_BRILLIANT_RING
                || effect == OverlayPrefs.EFFECT_BRILLIANT_CUT
                || effect == OverlayPrefs.EFFECT_RIPPLE_INK
                || effect == OverlayPrefs.EFFECT_GOOD_LOCK_POPPING
                || effect == OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE
                || effect == OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING
                || effect == OverlayPrefs.EFFECT_LG_G2_PARTICLE
                || effect == OverlayPrefs.EFFECT_LG_G2_CRYSTAL
                || effect == OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS
                || effect == OverlayPrefs.EFFECT_REVOLVING_GLASS;
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
        if (FoldDisplayTarget.backgroundProfiles(this).length > 1) {
            showProfileEffectBackgroundScreenshots(effect);
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

    private void showProfileEffectBackgroundScreenshots(int effect) {
        final ArrayList<Bitmap> previews = new ArrayList<Bitmap>();
        String[] profiles = FoldDisplayTarget.backgroundProfiles(this);
        File first = colormapScreenshotFileForPreview(effect, profiles[0]);
        File second = colormapScreenshotFileForPreview(effect, profiles[1]);
        if ((!first.exists() || first.length() <= 0L)
                && (!second.exists() || second.length() <= 0L)) {
            Toast.makeText(this, "No profile screenshots yet", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackground(pageBackground());
        root.addView(sectionTitle(FoldDisplayTarget.usesFoldProfiles(this)
                ? "Fold colormap screenshots" : "Tablet colormap screenshots"));
        root.addView(infoText("The two caches are independent. Missing profiles are shown explicitly."));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        rowParams.setMargins(0, dp(10), 0, dp(10));
        root.addView(row, rowParams);
        row.addView(profileScreenshotPreview(
                        FoldDisplayTarget.profileLabel(profiles[0]), first, previews),
                foldScreenshotColumnParams(false));
        row.addView(profileScreenshotPreview(
                        FoldDisplayTarget.profileLabel(profiles[1]), second, previews),
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

    private View profileScreenshotPreview(String label, File file, ArrayList<Bitmap> previews) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(10), dp(10), dp(10), dp(10));
        column.setBackground(infoBackground());
        column.addView(sectionLabel(label));
        if (file == null || !file.exists() || file.length() <= 0L) {
            TextView missing = infoText("No screenshot cached");
            missing.setGravity(Gravity.CENTER);
            column.addView(missing, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return column;
        }
        Argb8888BitmapStore.Info sourceBounds = Argb8888BitmapStore.inspect(file);
        Bitmap bitmap = decodeFoldPreviewBitmap(file);
        if (sourceBounds == null || bitmap == null || bitmap.isRecycled()) {
            TextView missing = infoText("Screenshot unreadable");
            missing.setGravity(Gravity.CENTER);
            column.addView(missing, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return column;
        }
        previews.add(bitmap);
        column.addView(infoText("source " + sourceBounds.width + " x "
                + sourceBounds.height + " \u2022 preview " + bitmap.getWidth() + " x "
                + bitmap.getHeight() + " \u2022 "
                + Math.max(1L, file.length() / 1024L) + " KB"));
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
        Bitmap poster = decodeEffectPreviewPoster(effect);
        if (poster == null) {
            poster = createEffectPreviewBitmap(effect, 720, 720);
        }
        showPreviewBubble(anchor, title, rawX, rawY,
                poster, true, effectPreviewAsset(effect));
    }

    private void showSeasonPreviewBubble(View anchor, int season, String title,
            float rawX, float rawY) {
        showPreviewBubble(anchor, title, rawX, rawY,
                decodeSeasonalPreviewPoster(season), true,
                seasonalPreviewAsset(season));
    }

    private void showPreviewBubble(View anchor, String title, float rawX, float rawY,
            Bitmap previewBitmap, boolean squarePreview, String videoAsset) {
        if (anchor == null || !anchor.isAttachedToWindow()
                || isFinishing() || isDestroyed()) {
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

        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackground(solidDrawable(Color.rgb(245, 248, 250), dp(14),
                Color.TRANSPARENT, 0));
        previewFrame.setClipToOutline(true);

        ImageView image = new ImageView(this);
        image.setImageBitmap(effectPreviewPopupBitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewFrame.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        int previewVideoGeneration = -1;
        if (videoAsset != null) {
            TextureView video = new TextureView(this);
            video.setAlpha(0f);
            previewFrame.addView(video, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            previewVideoGeneration = prepareEffectPreviewVideo(video, videoAsset);
        }
        card.addView(previewFrame, new LinearLayout.LayoutParams(
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
        final int popupVideoGeneration = previewVideoGeneration;
        effectPreviewPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                releaseEffectPreviewVideo(popupVideoGeneration);
            }
        });
        try {
            effectPreviewPopup.showAtLocation(getWindow().getDecorView(),
                    Gravity.NO_GRAVITY,
                    left,
                    top);
        } catch (RuntimeException error) {
            Log.w("LLEControl", "preview popup unavailable", error);
            hideEffectPreviewBubble();
            return;
        }
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .setInterpolator(tabEnterInterpolator())
                .start();
    }

    private void hideEffectPreviewBubble() {
        releaseEffectPreviewVideo();
        if (effectPreviewPopup != null) {
            effectPreviewPopup.dismiss();
            effectPreviewPopup = null;
        }
        if (effectPreviewPopupBitmap != null && !effectPreviewPopupBitmap.isRecycled()) {
            effectPreviewPopupBitmap.recycle();
        }
        effectPreviewPopupBitmap = null;
    }

    private String seasonalPreviewAsset(int seasonMode) {
        if (seasonMode == SeasonalDoodleView.SEASON_AUTO) {
            return "seasonal_preview_combined.mp4";
        }
        switch (resolveDoodlePreviewSeason(seasonMode)) {
            case SeasonalDoodleView.SEASON_SPRING:
                return "seasonal_preview_spring.mp4";
            case SeasonalDoodleView.SEASON_SUMMER:
                return "seasonal_preview_summer.mp4";
            case SeasonalDoodleView.SEASON_AUTUMN:
                return "seasonal_preview_autumn.mp4";
            case SeasonalDoodleView.SEASON_WINTER:
            default:
                return "seasonal_preview_winter.mp4";
        }
    }

    private Bitmap decodeSeasonalPreviewPoster(int seasonMode) {
        if (seasonMode == SeasonalDoodleView.SEASON_AUTO) {
            return BitmapFactory.decodeResource(
                    getResources(),
                    R.drawable.preview_doodle_seasonal_auto);
        }
        final int drawable;
        switch (resolveDoodlePreviewSeason(seasonMode)) {
            case SeasonalDoodleView.SEASON_SPRING:
                drawable = R.drawable.preview_doodle_seasonal_spring;
                break;
            case SeasonalDoodleView.SEASON_SUMMER:
                drawable = R.drawable.preview_doodle_seasonal_summer;
                break;
            case SeasonalDoodleView.SEASON_AUTUMN:
                drawable = R.drawable.preview_doodle_seasonal_autumn;
                break;
            case SeasonalDoodleView.SEASON_WINTER:
            default:
                drawable = R.drawable.preview_doodle_seasonal_winter;
                break;
        }
        return BitmapFactory.decodeResource(getResources(), drawable);
    }

    private String effectPreviewAsset(int effect) {
        switch (effect) {
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
                return "effect_preview_s3_ripple.mp4";
            case OverlayPrefs.EFFECT_N4_INK_IN_WATER:
                return "effect_preview_n2_ink_in_water.mp4";
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                return "effect_preview_s4_lens_flare.mp4";
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                return "effect_preview_s5_popping_colours.mp4";
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                return "effect_preview_n3_watercolor.mp4";
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
                return "effect_preview_n5_coloured_droplet.mp4";
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP:
                return "effect_preview_n5_sparkling_bubbles.mp4";
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return "effect_preview_n4_abstract_tiles.mp4";
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return "effect_preview_n4_geometric_mosaic.mp4";
            case OverlayPrefs.EFFECT_TABS_BLIND:
                return "effect_preview_tabs_blind.mp4";
            case OverlayPrefs.EFFECT_STONE_SKIPPING:
                return "effect_preview_s5_stone_skipping.mp4";
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED:
                return "effect_preview_s6_water_droplet.mp4";
            case OverlayPrefs.EFFECT_MASS_TENSION:
                return "effect_preview_mass_tension.mp4";
            case OverlayPrefs.EFFECT_BRILLIANT_RING:
                return "effect_preview_s5_brilliant_ring.mp4";
            case OverlayPrefs.EFFECT_BRILLIANT_CUT:
                return "effect_preview_tabs_brilliant_cut.mp4";
            case OverlayPrefs.EFFECT_GOOD_LOCK_POPPING:
                return "effect_preview_good_lock_popping.mp4";
            case OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE:
                return "effect_preview_good_lock_rectangle.mp4";
            case OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING:
                return "effect_preview_good_lock_bouncing.mp4";
            case OverlayPrefs.EFFECT_S3_NONE:
                return "effect_preview_s3_none.mp4";
            case OverlayPrefs.EFFECT_LG_G2_PIXELATE:
                return "effect_preview_lg_pixelate.mp4";
            case OverlayPrefs.EFFECT_LG_G1_WHITE_HOLE:
                return "effect_preview_lg_white_hole.mp4";
            case OverlayPrefs.EFFECT_LG_SODA:
                return "effect_preview_lg_soda.mp4";
            case OverlayPrefs.EFFECT_LG_G1_DEWDROP:
                return "effect_preview_lg_dewdrop.mp4";
            case OverlayPrefs.EFFECT_LG_G2_PARTICLE:
                return "effect_preview_lg_particle.mp4";
            case OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE:
                return "effect_preview_lg_light_particle.mp4";
            case OverlayPrefs.EFFECT_LG_G2_CRYSTAL:
                return "effect_preview_lg_crystal.mp4";
            case OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS:
                return "effect_preview_xperia_z1_blinds.mp4";
            case OverlayPrefs.EFFECT_SEASONAL_AUTO:
            case OverlayPrefs.EFFECT_SEASONAL_SPRING:
            case OverlayPrefs.EFFECT_SEASONAL_SUMMER:
            case OverlayPrefs.EFFECT_SEASONAL_AUTUMN:
            case OverlayPrefs.EFFECT_SEASONAL_WINTER:
                return seasonalUnlockEffectPreviewAsset(effect);
            default:
                return null;
        }
    }

    private String seasonalUnlockEffectPreviewAsset(int effect) {
        switch (resolveDoodlePreviewSeason(OverlayPrefs.seasonForUnlockEffect(effect))) {
            case SeasonalDoodleView.SEASON_SPRING:
                return "effect_preview_seasonal_spring.mp4";
            case SeasonalDoodleView.SEASON_SUMMER:
                return "effect_preview_seasonal_summer.mp4";
            case SeasonalDoodleView.SEASON_AUTUMN:
                return "effect_preview_seasonal_autumn.mp4";
            case SeasonalDoodleView.SEASON_WINTER:
            default:
                return "effect_preview_seasonal_winter.mp4";
        }
    }

    private Bitmap decodeEffectPreviewPoster(int effect) {
        final int drawable;
        switch (effect) {
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
                drawable = R.drawable.effect_preview_s3_ripple;
                break;
            case OverlayPrefs.EFFECT_N4_INK_IN_WATER:
                drawable = R.drawable.effect_preview_n2_ink_in_water;
                break;
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                drawable = R.drawable.effect_preview_s4_lens_flare;
                break;
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                drawable = R.drawable.effect_preview_s5_popping_colours;
                break;
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                drawable = R.drawable.effect_preview_n3_watercolor;
                break;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
                drawable = R.drawable.effect_preview_n5_coloured_droplet;
                break;
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP:
                drawable = R.drawable.effect_preview_n5_sparkling_bubbles;
                break;
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED:
                drawable = R.drawable.preview_unlock_s6_water_droplet_lle;
                break;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                drawable = R.drawable.effect_preview_n4_abstract_tiles;
                break;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                drawable = R.drawable.effect_preview_n4_geometric_mosaic;
                break;
            case OverlayPrefs.EFFECT_TABS_BLIND:
                drawable = R.drawable.effect_preview_tabs_blind;
                break;
            case OverlayPrefs.EFFECT_STONE_SKIPPING:
                drawable = R.drawable.effect_preview_s5_stone_skipping;
                break;
            case OverlayPrefs.EFFECT_MASS_TENSION:
                drawable = R.drawable.preview_unlock_mass_tension;
                break;
            case OverlayPrefs.EFFECT_BRILLIANT_RING:
                drawable = R.drawable.effect_preview_s5_brilliant_ring;
                break;
            case OverlayPrefs.EFFECT_BRILLIANT_CUT:
                drawable = R.drawable.effect_preview_tabs_brilliant_cut;
                break;
            case OverlayPrefs.EFFECT_GOOD_LOCK_POPPING:
                drawable = R.drawable.effect_preview_good_lock_popping;
                break;
            case OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE:
                drawable = R.drawable.effect_preview_good_lock_rectangle;
                break;
            case OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING:
                drawable = R.drawable.effect_preview_good_lock_bouncing;
                break;
            case OverlayPrefs.EFFECT_S3_NONE:
                drawable = R.drawable.effect_preview_s3_none;
                break;
            case OverlayPrefs.EFFECT_LG_G2_PIXELATE:
                drawable = R.drawable.effect_preview_lg_pixelate;
                break;
            case OverlayPrefs.EFFECT_LG_G1_WHITE_HOLE:
                drawable = R.drawable.effect_preview_lg_white_hole;
                break;
            case OverlayPrefs.EFFECT_LG_SODA:
                drawable = R.drawable.effect_preview_lg_soda;
                break;
            case OverlayPrefs.EFFECT_LG_G1_DEWDROP:
                drawable = R.drawable.effect_preview_lg_dewdrop;
                break;
            case OverlayPrefs.EFFECT_LG_G2_PARTICLE:
                drawable = R.drawable.effect_preview_lg_particle;
                break;
            case OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE:
                drawable = R.drawable.effect_preview_lg_light_particle;
                break;
            case OverlayPrefs.EFFECT_LG_G2_CRYSTAL:
                drawable = R.drawable.effect_preview_lg_crystal;
                break;
            case OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS:
                drawable = R.drawable.effect_preview_xperia_z1_blinds;
                break;
            case OverlayPrefs.EFFECT_SEASONAL_AUTO:
            case OverlayPrefs.EFFECT_SEASONAL_SPRING:
            case OverlayPrefs.EFFECT_SEASONAL_SUMMER:
            case OverlayPrefs.EFFECT_SEASONAL_AUTUMN:
            case OverlayPrefs.EFFECT_SEASONAL_WINTER:
                drawable = seasonalUnlockEffectPreviewPosterResId(effect);
                break;
            default:
                return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(getResources(), drawable, options);
    }

    private int seasonalUnlockEffectPreviewPosterResId(int effect) {
        switch (resolveDoodlePreviewSeason(OverlayPrefs.seasonForUnlockEffect(effect))) {
            case SeasonalDoodleView.SEASON_SPRING:
                return R.drawable.effect_preview_seasonal_spring;
            case SeasonalDoodleView.SEASON_SUMMER:
                return R.drawable.effect_preview_seasonal_summer;
            case SeasonalDoodleView.SEASON_AUTUMN:
                return R.drawable.effect_preview_seasonal_autumn;
            case SeasonalDoodleView.SEASON_WINTER:
            default:
                return R.drawable.effect_preview_seasonal_winter;
        }
    }

    private int prepareEffectPreviewVideo(final TextureView texture,
            final String assetPath) {
        final int generation = ++effectPreviewVideoGeneration;
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surfaceTexture,
                    int width, int height) {
                if (generation == effectPreviewVideoGeneration) {
                    startEffectPreviewVideo(
                            texture, surfaceTexture, assetPath, generation);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(
                    android.graphics.SurfaceTexture surfaceTexture,
                    int width, int height) {
                // CENTER_CROP scaling is applied by the square source and destination.
            }

            @Override
            public boolean onSurfaceTextureDestroyed(
                    android.graphics.SurfaceTexture surfaceTexture) {
                releaseEffectPreviewVideo(generation);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(
                    android.graphics.SurfaceTexture surfaceTexture) {
                // MediaPlayer owns frame delivery.
            }
        });
        return generation;
    }

    private void startEffectPreviewVideo(final TextureView texture,
            android.graphics.SurfaceTexture surfaceTexture, String assetPath,
            final int generation) {
        if (generation != effectPreviewVideoGeneration
                || effectPreviewPopup == null) {
            return;
        }
        releaseEffectPreviewVideoResources();
        AssetFileDescriptor descriptor = null;
        final MediaPlayer player = new MediaPlayer();
        try {
            descriptor = getAssets().openFd(assetPath);
            effectPreviewVideoSurface = new Surface(surfaceTexture);
            effectPreviewMediaPlayer = player;
            player.setSurface(effectPreviewVideoSurface);
            player.setDataSource(descriptor.getFileDescriptor(),
                    descriptor.getStartOffset(), descriptor.getLength());
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer preparedPlayer) {
                    if (generation != effectPreviewVideoGeneration
                            || preparedPlayer != effectPreviewMediaPlayer
                            || effectPreviewPopup == null) {
                        return;
                    }
                    preparedPlayer.start();
                }
            });
            player.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer infoPlayer, int what, int extra) {
                    if (generation == effectPreviewVideoGeneration
                            && infoPlayer == effectPreviewMediaPlayer
                            && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        texture.animate().alpha(1f).setDuration(100L).start();
                    }
                    return false;
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer failedPlayer, int what, int extra) {
                    Log.w("LLEControl", "effect preview video failed what="
                            + what + " extra=" + extra);
                    if (generation == effectPreviewVideoGeneration
                            && failedPlayer == effectPreviewMediaPlayer) {
                        releaseEffectPreviewVideo(generation);
                    }
                    return true;
                }
            });
            player.prepareAsync();
        } catch (IOException | RuntimeException error) {
            Log.w("LLEControl", "effect preview asset unavailable path="
                    + assetPath, error);
            if (generation == effectPreviewVideoGeneration
                    && effectPreviewMediaPlayer == player) {
                releaseEffectPreviewVideo(generation);
            } else {
                try {
                    player.release();
                } catch (RuntimeException ignored) {
                }
            }
        } finally {
            if (descriptor != null) {
                try {
                    descriptor.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void releaseEffectPreviewVideo() {
        effectPreviewVideoGeneration++;
        releaseEffectPreviewVideoResources();
    }

    private void releaseEffectPreviewVideo(int generation) {
        if (generation < 0 || generation != effectPreviewVideoGeneration) {
            return;
        }
        effectPreviewVideoGeneration++;
        releaseEffectPreviewVideoResources();
    }

    private void releaseEffectPreviewVideoResources() {
        if (effectPreviewMediaPlayer != null) {
            try {
                effectPreviewMediaPlayer.setSurface(null);
            } catch (RuntimeException ignored) {
            }
            try {
                effectPreviewMediaPlayer.release();
            } catch (RuntimeException ignored) {
            }
            effectPreviewMediaPlayer = null;
        }
        if (effectPreviewVideoSurface != null) {
            effectPreviewVideoSurface.release();
            effectPreviewVideoSurface = null;
        }
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

    private Bitmap createEffectPreviewBitmap(int effect, int width, int height) {
        if (OverlayPrefs.isSeasonalUnlockEffect(effect)) {
            return createSeasonalUnlockPreviewBitmap(effect, width, height);
        }
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
            case OverlayPrefs.EFFECT_N4_INK_IN_WATER:
                drawPreviewRipple(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                drawPreviewPoppingColours(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_GOOD_LOCK_POPPING:
            case OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE:
            case OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING:
                drawPreviewGoodLockParticles(canvas, paint, effect, width, height);
                break;
            case OverlayPrefs.EFFECT_TABS_BLIND:
                drawPreviewBlind(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                drawPreviewWatercolor(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED:
                drawPreviewDroplets(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP:
                drawPreviewBubbles(canvas, paint, width, height);
                break;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                drawPreviewTiles(canvas, paint, width, height, false);
                break;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                drawPreviewTiles(canvas, paint, width, height, true);
                break;
            case OverlayPrefs.EFFECT_BRILLIANT_CUT:
                drawEffectMotif(canvas, paint, effect,
                        new RectF(width * 0.22f, height * 0.22f,
                                width * 0.78f, height * 0.78f),
                        Color.rgb(222, 246, 255), 0.95f);
                break;
            default:
                drawPreviewLensFlare(canvas, paint, width, height);
                break;
        }
        drawPreviewVignette(canvas, paint, width, height);
        return bitmap;
    }

    private Bitmap createSeasonalUnlockPreviewBitmap(int effect, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        drawGracePreviewBackground(canvas, paint, width, height, false);
        int season = resolveDoodlePreviewSeason(
                OverlayPrefs.seasonForUnlockEffect(effect));
        drawDoodleSeasonalParticles(canvas, paint, width, height, season);
        drawEffectMotif(canvas, paint, effect,
                new RectF(width * 0.27f, height * 0.27f,
                        width * 0.73f, height * 0.73f),
                Color.WHITE, 0.96f);
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
            case OverlayPrefs.EFFECT_N4_INK_IN_WATER:
                return R.drawable.preview_unlock_s3_ripple_lle;
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                return R.drawable.preview_unlock_s4_lens_flare_lle;
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                return R.drawable.preview_unlock_n3_watercolor_lle;
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                return R.drawable.preview_unlock_s5_popping_colours_lle;
            case OverlayPrefs.EFFECT_BRILLIANT_RING:
                return R.drawable.preview_unlock_brilliantring_s5;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return R.drawable.preview_unlock_n4_abstract_tiles_lle;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return R.drawable.preview_unlock_n4_geometric_mosaic_lle;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP:
                return R.drawable.preview_unlock_n5_colored_droplet_lle;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
                return R.drawable.preview_unlock_n5_colored_droplet_gyro_lle;
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP:
                return R.drawable.preview_unlock_n5_sparkling_bubbles_lle;
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED:
                return R.drawable.preview_unlock_s6_water_droplet_lle;
            case OverlayPrefs.EFFECT_STONE_SKIPPING:
                return R.drawable.preview_unlock_stoneskipping_s5;
            case OverlayPrefs.EFFECT_MASS_TENSION:
                return R.drawable.preview_unlock_mass_tension;
            case OverlayPrefs.EFFECT_LG_SODA:
                return R.drawable.preview_unlock_lg_soda;
            case OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE:
                return R.drawable.lg_lightparticle_bg;
            default:
                return 0;
        }
    }

    private int effectIconResId(int effect) {
        switch (effect) {
            case OverlayPrefs.EFFECT_S3_NONE:
                return R.drawable.icon_effect_s3_none_lle;
            case OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE:
                return R.drawable.icon_effect_s3_ripple_lle;
            case OverlayPrefs.EFFECT_S4_LENS_FLARE:
                return R.drawable.icon_effect_s4_lens_flare_lle;
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                return R.drawable.icon_effect_n3_watercolor_lle;
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                return R.drawable.icon_effect_s5_popping_colours_lle;
            case OverlayPrefs.EFFECT_GOOD_LOCK_POPPING:
                return R.drawable.icon_effect_s5_popping_colours_lle;
            case OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE:
                return R.drawable.icon_effect_n4_abstract_tiles_lle;
            case OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING:
                return R.drawable.icon_effect_n5_colored_droplet_lle;
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return R.drawable.icon_effect_n4_abstract_tiles_lle;
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return R.drawable.icon_effect_n4_geometric_mosaic_lle;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP:
                return R.drawable.icon_effect_n5_colored_droplet_lle;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
                return R.drawable.icon_effect_n5_colored_droplet_gyro_lle;
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP:
                return R.drawable.icon_effect_n5_sparkling_bubbles_lle;
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED:
                return R.drawable.icon_effect_s6_water_droplet_lle;
            case OverlayPrefs.EFFECT_N4_INK_IN_WATER:
                return R.drawable.icon_effect_n3_ink_in_water_lle;
            case OverlayPrefs.EFFECT_RIPPLE_INK:
                return R.drawable.icon_effect_n3_ink_in_water_lle;
            case OverlayPrefs.EFFECT_STONE_SKIPPING:
                return R.drawable.icon_effect_s5_stone_skipping_lle;
            case OverlayPrefs.EFFECT_MASS_TENSION:
                return R.drawable.icon_effect_mass_tension;
            case OverlayPrefs.EFFECT_LG_SODA:
                return R.drawable.icon_effect_lg_soda_lle;
            case OverlayPrefs.EFFECT_LG_G1_DEWDROP:
                return R.drawable.icon_effect_lg_dewdrop_lle;
            case OverlayPrefs.EFFECT_LG_G2_PARTICLE:
                return R.drawable.icon_effect_g2_particle_lle;
            case OverlayPrefs.EFFECT_LG_G2_LIGHT_PARTICLE:
                return R.drawable.icon_effect_g2_light_particle_lle;
            case OverlayPrefs.EFFECT_LG_G1_WHITE_HOLE:
                return R.drawable.icon_effect_white_hole_lle;
            case OverlayPrefs.EFFECT_LG_G2_PIXELATE:
                return R.drawable.icon_effect_g2_pixelate_lle;
            case OverlayPrefs.EFFECT_LG_G2_CRYSTAL:
                return R.drawable.icon_effect_g2_crystal_lle;
            case OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS:
                return R.drawable.icon_effect_xperia_z1_blinds_lle;
            case OverlayPrefs.EFFECT_REVOLVING_GLASS:
                return R.drawable.icon_effect_revolving_glass_lle;
            case OverlayPrefs.EFFECT_BRILLIANT_RING:
                return R.drawable.icon_effect_s5_brilliant_ring_lle;
            case OverlayPrefs.EFFECT_TABS_BLIND:
                return R.drawable.icon_effect_tabs_blind_lle;
            case OverlayPrefs.EFFECT_BRILLIANT_CUT:
                return R.drawable.icon_effect_tabs_brilliant_cut_lle;
            case OverlayPrefs.EFFECT_SEASONAL_AUTO:
                return R.drawable.icon_effect_seasonal_auto_lle;
            case OverlayPrefs.EFFECT_SEASONAL_SPRING:
                return R.drawable.icon_effect_seasonal_spring_lle;
            case OverlayPrefs.EFFECT_SEASONAL_SUMMER:
                return R.drawable.icon_effect_seasonal_summer_lle;
            case OverlayPrefs.EFFECT_SEASONAL_AUTUMN:
                return R.drawable.icon_effect_seasonal_autumn_lle;
            case OverlayPrefs.EFFECT_SEASONAL_WINTER:
                return R.drawable.icon_effect_seasonal_winter_lle;
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

    private void showTesterUnderlayProbe() {
        File result = ChargingAccessibilityService.testerUnderlayProbeFile(this);
        Argb8888BitmapStore.Info bounds = Argb8888BitmapStore.inspect(result);
        if (bounds == null) {
            Toast.makeText(this,
                    "No underlay image. "
                            + ChargingAccessibilityService.testerUnderlayProbeStatus(this),
                    Toast.LENGTH_LONG).show();
            return;
        }
        final Bitmap bitmap = decodePreviewBitmap(result);
        if (bitmap == null || bitmap.isRecycled()) {
            Toast.makeText(this, "Underlay probe result unreadable", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackground(pageBackground());
        root.addView(sectionTitle("LG underlay API probe"));
        root.addView(infoText(ChargingAccessibilityService.testerUnderlayProbeStatus(this)
                + "\nprivate ARGB8888 | source " + bounds.width + " x " + bounds.height
                + " | preview " + bitmap.getWidth() + " x " + bitmap.getHeight()));

        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        imageParams.setMargins(0, dp(8), 0, dp(10));
        root.addView(image, imageParams);
        root.addView(infoText(
                "Interpretation: launcher/app pixels mean the API can drive a future LG "
                        + "underlay source. A SystemUI-only/no-window failure means it cannot."));
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

    /** App-owned static picker preview; production motion remains in the renderer. */
    private void drawPreviewGoodLockParticles(Canvas canvas, Paint paint, int effect,
            int width, int height) {
        if (effect == OverlayPrefs.EFFECT_GOOD_LOCK_POPPING) {
            drawPreviewPoppingColours(canvas, paint, width, height);
            return;
        }
        if (effect == OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE) {
            drawPreviewTiles(canvas, paint, width, height, false);
            return;
        }
        drawPreviewBubbles(canvas, paint, width, height);
    }

    private void drawPreviewBlind(Canvas canvas, Paint paint, int width, int height) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        float top = height * 0.30f;
        float bottom = height * 0.78f;
        float centerX = width * 0.51f;
        int columns = 12;
        float baseWidth = width * 0.62f / columns;
        for (int i = 0; i < columns; i++) {
            float distance = Math.abs((i + 0.5f) - columns * 0.53f);
            float scale = 1f + Math.max(0f, 1f - distance / 4.2f) * 0.42f;
            float left = width * 0.19f + i * baseWidth;
            int shade = Math.max(0, Math.min(255, Math.round(116f + scale * 54f)));
            paint.setColor(Color.argb(225, shade, shade + 12, shade + 22));
            canvas.save();
            canvas.scale(scale, scale, left + baseWidth * 0.5f, (top + bottom) * 0.5f);
            canvas.drawRect(left, top, left + baseWidth - dp(1), bottom, paint);
            canvas.restore();
        }
        paint.setShader(new RadialGradient(centerX, height * 0.55f, width * 0.26f,
                new int[] {Color.argb(120, 255, 255, 255), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, height * 0.55f, width * 0.26f, paint);
        paint.setShader(null);
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
        if (OverlayPrefs.importedEffectBackgroundEnabled(this, effect, profile)) {
            File imported = OverlayPrefs.importedEffectBackgroundFile(this, effect, profile);
            return imported == null
                    ? new File(getFilesDir(), "missing_imported_effect_background")
                    : imported;
        }
        File shared = OverlayPrefs.effectBackgroundFile(this, effect, profile);
        if (shared.exists() && shared.length() > 0L) {
            return shared;
        }
        File legacyProfile = OverlayPrefs.legacyPngEffectBackgroundFile(this, profile);
        if (legacyProfile.exists() && legacyProfile.length() > 0L
                && copyColormapScreenshotFile(legacyProfile, shared)) {
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
                OverlayPrefs.EFFECT_N4_INK_IN_WATER,
                OverlayPrefs.EFFECT_S5_POPPING_COLOURS,
                OverlayPrefs.EFFECT_TABS_BLIND,
                OverlayPrefs.EFFECT_WATERCOLOUR,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP,
                OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES,
                OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP,
                OverlayPrefs.EFFECT_S6_WATER_DROPLET,
                OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC,
                OverlayPrefs.EFFECT_BRILLIANT_RING,
                OverlayPrefs.EFFECT_BRILLIANT_CUT,
                OverlayPrefs.EFFECT_RIPPLE_INK,
                OverlayPrefs.EFFECT_GOOD_LOCK_POPPING,
                OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE,
                OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING,
                OverlayPrefs.EFFECT_LG_G2_PIXELATE,
                OverlayPrefs.EFFECT_LG_G2_PARTICLE,
                OverlayPrefs.EFFECT_LG_G2_CRYSTAL,
                OverlayPrefs.EFFECT_XPERIA_Z1_BLINDS,
                OverlayPrefs.EFFECT_REVOLVING_GLASS
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
        try {
            return Argb8888BitmapStore.migrate(source, target);
        } catch (Throwable t) {
            Log.d("LLEControl", "colormap screenshot migration failed", t);
            return false;
        }
    }

    private Bitmap decodePreviewBitmap(File file) {
        Argb8888BitmapStore.Info bounds = Argb8888BitmapStore.inspect(file);
        if (bounds == null) {
            return null;
        }
        return Argb8888BitmapStore.decode(
                file, previewSampleSize(bounds.width, bounds.height));
    }

    private Bitmap decodeFoldPreviewBitmap(File file) {
        Argb8888BitmapStore.Info bounds = Argb8888BitmapStore.inspect(file);
        if (bounds == null) {
            return null;
        }
        int sample = 1;
        while (Math.max(bounds.width, bounds.height) / sample > 960) {
            sample *= 2;
        }
        return Argb8888BitmapStore.decode(file, sample);
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
            int current, boolean lineEnabled) {
        return effectOption(
                title,
                subtitle,
                OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                current == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES,
                lineEnabled ? 1 : 0,
                abstractTilesVariantControls(lineEnabled));
    }

    private View effectOption(String title, String subtitle, final int value,
            final boolean selected, final int abstractTilesLineMode) {
        return effectOption(title, subtitle, value, selected, abstractTilesLineMode, null);
    }

    private View effectOption(String title, String subtitle, final int value,
            final boolean selected, final int abstractTilesLineMode,
            View variantControls) {
        final boolean randomMode = OverlayPrefs.randomUnlockEffectEnabled(this);
        final boolean randomPoolEditing = randomMode && randomPoolEditMode;
        final boolean randomEligible = OverlayPrefs.isRandomUnlockEffectEligible(this, value);
        final boolean randomHighCost = randomEligible
                && OverlayPrefs.isRandomUnlockEffectExcludedForCost(value);
        final boolean[] includedInRandom = new boolean[] {
                randomEligible && OverlayPrefs.randomUnlockEffectSelected(this, value)
        };
        final boolean fixedSelected = selected && !randomMode;
        final boolean blockedByNoColormap =
                OverlayPrefs.testerNoColormapModeEnabled(this)
                && !OverlayPrefs.supportsTesterNoColormapMode(value);
        final LinearLayout card = verticalGroup();
        final LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(11), dp(12), dp(11));
        header.setMinimumHeight(dp(82));
        final boolean[] previewOpened = new boolean[] {false};
        final float[] previewDown = new float[] {0f, 0f};
        final Runnable previewRunnable = new Runnable() {
            @Override
            public void run() {
                if (randomPoolEditing || blockedByNoColormap
                        || tabSwipeDragging || tabAnimationRunning) {
                    return;
                }
                previewOpened[0] = true;
                header.setPressed(false);
                showEffectPreviewBubble(header, value, title, previewDown[0], previewDown[1]);
            }
        };
        header.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (blockedByNoColormap) {
                    return false;
                }
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
        card.setBackground(optionBackground(
                randomPoolEditing ? includedInRandom[0] : fixedSelected));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation((randomPoolEditing && includedInRandom[0]) || fixedSelected
                    ? dp(4) : dp(1));
        }

        FrameLayout marker = new FrameLayout(this);
        marker.addView(new GraceEffectIconView(value, fixedSelected),
                new FrameLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        markerParams.setMargins(0, 0, dp(14), 0);
        header.addView(marker, markerParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        header.addView(copy, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.TOP | Gravity.LEFT);
        copy.addView(titleLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(16f);
        titleView.setTypeface(Typeface.DEFAULT,
                fixedSelected || (randomPoolEditing && includedInRandom[0])
                        ? Typeface.BOLD : Typeface.NORMAL);
        titleView.setSingleLine(false);
        titleLine.addView(titleView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView subtitleView = new TextView(this);
        String displayedSubtitle = blockedByNoColormap
                ? subtitle + "\nRequires a lockscreen colormap."
                : subtitle;
        if (randomPoolEditing && randomHighCost) {
            displayedSubtitle += "\nHigh resource use - double confirmation required.";
        }
        subtitleView.setText(displayedSubtitle);
        subtitleView.setTextColor(COLOR_MUTED);
        subtitleView.setTextSize(13f);
        subtitleView.setSingleLine(false);
        subtitleView.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(2), 0, 0);
        copy.addView(subtitleView, subtitleParams);

        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (blockedByNoColormap) {
                    Toast.makeText(ControlActivity.this,
                            "This effect requires a lockscreen colormap. Choose another "
                                    + "wallpaper mode in Setup to enable it.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (previewOpened[0]) {
                    previewOpened[0] = false;
                    return;
                }
                if (randomPoolEditing) {
                    if (!randomEligible) {
                        Toast.makeText(ControlActivity.this,
                                "This effect is not compatible with the current build or "
                                        + "wallpaper mode.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (includedInRandom[0]) {
                        applyRandomPoolSelection(value, false, randomHighCost,
                                includedInRandom, card, titleView);
                    } else if (randomHighCost) {
                        confirmHighCostRandomEffect(title, value, includedInRandom,
                                card, titleView);
                    } else {
                        applyRandomPoolSelection(value, true, false,
                                includedInRandom, card, titleView);
                    }
                    return;
                }
                randomPoolEditMode = false;
                OverlayPrefs.setRandomUnlockEffectEnabled(ControlActivity.this, false);
                if (abstractTilesLineMode >= 0) {
                    queueAbstractTilesSelection(abstractTilesLineMode == 1);
                } else {
                    queueUnlockEffectSelection(value);
                }
                showTab(selectedTab);
            }
        });

        card.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (variantControls != null && !blockedByNoColormap && !randomPoolEditing) {
            card.addView(variantControls);
        }
        if (!blockedByNoColormap
                && !randomPoolEditing
                && supportsPerEffectHighFrameRate(value)
                && hasInternalHighRefreshDisplay()) {
            card.addView(perEffectHighFrameRateControls(value));
        }
        if (blockedByNoColormap || (randomPoolEditing && !randomEligible)) {
            card.setAlpha(0.42f);
        } else if (randomPoolEditing && randomHighCost && !includedInRandom[0]) {
            card.setAlpha(0.68f);
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(rowParams);
        return card;
    }

    private void applyRandomPoolSelection(int effect, boolean selected, boolean highCost,
            boolean[] includedInRandom, LinearLayout card, TextView titleView) {
        includedInRandom[0] = selected;
        OverlayPrefs.setRandomUnlockEffectSelected(this, effect, selected);
        card.setBackground(optionBackground(selected));
        card.setAlpha(!selected && highCost ? 0.68f : 1f);
        titleView.setTypeface(Typeface.DEFAULT,
                selected ? Typeface.BOLD : Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(selected ? dp(4) : dp(1));
        }
        updateRandomPoolSummary();
    }

    private void confirmHighCostRandomEffect(final String title, final int effect,
            final boolean[] includedInRandom, final LinearLayout card,
            final TextView titleView) {
        new AlertDialog.Builder(this)
                .setTitle("Are you sure?")
                .setMessage(title + " is a resource-heavy effect and may increase loading "
                        + "time, memory use, and unlock lag when selected by Random.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new AlertDialog.Builder(ControlActivity.this)
                                .setTitle("Are you sure sure?")
                                .setMessage("This uses a lot of resources! Enable " + title
                                        + " in the Random pool anyway?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Add anyway",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface secondDialog,
                                                    int secondWhich) {
                                                applyRandomPoolSelection(effect, true, true,
                                                        includedInRandom, card, titleView);
                                            }
                                        })
                                .show();
                    }
                })
                .show();
    }

    private View randomEffectOption() {
        final boolean selected = OverlayPrefs.randomUnlockEffectEnabled(this);
        if (!selected) {
            randomPoolEditMode = false;
        }
        int selectedCount = OverlayPrefs.randomUnlockEffectPool(this).size();
        LinearLayout card = verticalGroup();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(11), dp(12), dp(11));
        header.setMinimumHeight(dp(82));
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uiHandler.removeCallbacks(applyPendingUnlockEffectRunnable);
                persistPendingUnlockEffect(false);
                if (!selected) {
                    OverlayPrefs.setRandomUnlockEffectEnabled(ControlActivity.this, true);
                    randomPoolEditMode = false;
                } else {
                    randomPoolEditMode = !randomPoolEditMode;
                }
                showTab(selectedTab, false, 0);
            }
        });

        card.setBackground(optionBackground(selected));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(selected ? dp(4) : dp(1));
        }

        View marker = new RandomEffectIconView(selected);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        markerParams.setMargins(0, 0, dp(14), 0);
        header.addView(marker, markerParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        header.addView(copy, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.TOP | Gravity.LEFT);
        copy.addView(titleLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(this);
        titleView.setText("Random");
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(16f);
        titleView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        titleLine.addView(titleView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (selected) {
            TextView editPool = randomPoolEditButton(randomPoolEditMode ? "DONE" : "EDIT POOL");
            editPool.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    randomPoolEditMode = !randomPoolEditMode;
                    showTab(selectedTab, false, 0);
                }
            });
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
            editParams.setMargins(dp(8), 0, 0, 0);
            titleLine.addView(editPool, editParams);
        }

        TextView subtitleView = new TextView(this);
        randomPoolSummaryView = subtitleView;
        subtitleView.setText(selected
                ? randomPoolEditMode
                        ? selectedCount + " selected. Tap whole cards; teal means enabled."
                        : selectedCount + " effects selected. Tap EDIT POOL to change them."
                : "Cycle through compatible effects. Heavy renderers require two confirmations.");
        subtitleView.setTextColor(COLOR_MUTED);
        subtitleView.setTextSize(13f);
        subtitleView.setSingleLine(false);
        subtitleView.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(2), 0, 0);
        copy.addView(subtitleView, subtitleParams);

        card.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(4), 0, dp(8));
        card.setLayoutParams(rowParams);
        return card;
    }

    private TextView randomPoolEditButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(10.5f);
        button.setTextColor(randomPoolEditMode ? Color.WHITE : COLOR_ACCENT_DEEP);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setMinWidth(dp(68));
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(randomPoolEditMode
                ? solidDrawable(COLOR_ACCENT_DEEP, dp(15), Color.TRANSPARENT, 0)
                : solidDrawable(Color.argb(125, 239, 250, 250), dp(15),
                        Color.argb(145, 33, 158, 166), dp(1)));
        return button;
    }

    private void updateRandomPoolSummary() {
        if (randomPoolSummaryView == null) {
            return;
        }
        int selectedCount = OverlayPrefs.randomUnlockEffectPool(this).size();
        randomPoolSummaryView.setText(randomPoolEditMode
                ? selectedCount + " selected. Tap whole cards; teal means enabled."
                : selectedCount + " effects selected. Tap EDIT POOL to change them.");
    }

    private View abstractTilesVariantControls(boolean lineEnabled) {
        final Switch lines = compactEffectVariantSwitch("Lines", lineEnabled);
        lines.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(
                        OverlayPrefs.ABSTRACT_TILES_LINE_ENABLED, isChecked).apply();
                if (pendingUnlockEffect == OverlayPrefs.EFFECT_S4_ABSTRACT_TILES) {
                    pendingAbstractTilesLineMode = isChecked ? 1 : 0;
                }
                showTab(selectedTab);
            }
        });
        return effectVariantControls(lines);
    }

    private View colourDropletEffectOption(int current) {
        boolean gyroEnabled;
        if (pendingUnlockEffect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP
                || pendingUnlockEffect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP) {
            gyroEnabled = OverlayPrefs.isColourDropletGyroEffect(pendingUnlockEffect);
        } else {
            gyroEnabled = OverlayPrefs.colourDropletGyroEnabled(this);
        }
        final int effect = gyroEnabled
                ? OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                : OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP;
        return effectOption(
                "N5 Colored Droplet",
                "Colorful liquid droplets rolling across screen.",
                effect,
                OverlayPrefs.isColourDropletEffect(current),
                -1,
                colourDropletVariantControls(gyroEnabled, effect));
    }

    private View colourDropletVariantControls(boolean gyroEnabled, final int sourceEffect) {
        final Switch gyro = compactEffectVariantSwitch("Gyro", gyroEnabled);
        gyro.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int targetEffect = isChecked
                        ? OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP
                        : OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP;
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean(OverlayPrefs.N5_COLOUR_DROPLET_GYRO_ENABLED, isChecked)
                        .putBoolean(
                                OverlayPrefs.experimentalNativeRefreshPhysicsKey(targetEffect),
                                OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(
                                        ControlActivity.this, sourceEffect))
                        .putInt(
                                OverlayPrefs.experimentalNativeRefreshPhysicsSpeedTenthsKey(
                                        targetEffect),
                                OverlayPrefs.experimentalNativeRefreshPhysicsSpeedTenths(
                                        ControlActivity.this, sourceEffect));
                int currentEffect = OverlayPrefs.unlockEffect(ControlActivity.this);
                if (pendingUnlockEffect == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP
                        || pendingUnlockEffect
                        == OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP) {
                    pendingUnlockEffect = targetEffect;
                } else if (OverlayPrefs.isColourDropletEffect(currentEffect)
                        && pendingUnlockEffect < 0) {
                    editor.putInt(OverlayPrefs.UNLOCK_EFFECT, targetEffect);
                }
                editor.apply();
                showTab(selectedTab);
            }
        });
        return effectVariantControls(gyro);
    }

    private View lensFlareEffectOption(int current) {
        return effectOption(
                "S4 Lens Flare",
                "A bright flare blooms under your touch in four selectable light styles.",
                OverlayPrefs.EFFECT_S4_LENS_FLARE,
                current == OverlayPrefs.EFFECT_S4_LENS_FLARE,
                -1,
                lensFlareVariantControls());
    }

    private View lensFlareVariantControls() {
        final String selectedMode = OverlayPrefs.lensFlareMode(this);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), 0, dp(12), dp(8));

        TextView label = new TextView(this);
        label.setText("Flare style");
        label.setTextColor(COLOR_ACCENT_DEEP);
        label.setTextSize(12f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setIncludeFontPadding(false);
        controls.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(20)));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        final String[] modes = {
                OverlayPrefs.LENS_FLARE_MODE_FLARE,
                OverlayPrefs.LENS_FLARE_MODE_BLUE_RING,
                OverlayPrefs.LENS_FLARE_MODE_BLOOD,
                OverlayPrefs.LENS_FLARE_MODE_LIGHTNING
        };
        final String[] names = {"Original", "Blue Ring", "Blood", "Lightning"};
        for (int index = 0; index < modes.length; index++) {
            final String mode = modes[index];
            LensFlareModeSwatchView swatch = new LensFlareModeSwatchView(
                    lensFlareModePreviewDrawable(mode), names[index],
                    mode.equals(selectedMode));
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.edit().putString(OverlayPrefs.LENS_FLARE_MODE, mode).apply();
                    showTab(selectedTab);
                }
            });
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                    0, dp(40), 1f);
            if (index > 0) {
                swatchParams.setMargins(dp(2), 0, 0, 0);
            }
            swatches.addView(swatch, swatchParams);
        }
        scroller.addView(swatches, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.MATCH_PARENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        controls.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        return controls;
    }

    private int lensFlareModePreviewDrawable(String mode) {
        if (OverlayPrefs.LENS_FLARE_MODE_BLUE_RING.equals(mode)) {
            return R.drawable.keyguard_bluering_light_00040;
        }
        if (OverlayPrefs.LENS_FLARE_MODE_BLOOD.equals(mode)) {
            return R.drawable.keyguard_blood_light_00040;
        }
        if (OverlayPrefs.LENS_FLARE_MODE_LIGHTNING.equals(mode)) {
            return R.drawable.keyguard_lightning_light_00040;
        }
        return R.drawable.keyguard_flare_light_00040;
    }

    /** Note 3 Ripple Ink card, available on the production ARM64 renderer path. */
    private View rippleInkEffectOption(int current) {
        return effectOption(
                "N3 Ripple Ink",
                "Ink ripples with a selectable colour palette.",
                OverlayPrefs.EFFECT_RIPPLE_INK,
                current == OverlayPrefs.EFFECT_RIPPLE_INK,
                -1,
                rippleInkPaletteControls());
    }

    private View rippleInkPaletteControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), 0, dp(12), dp(10));

        TextView label = new TextView(this);
        label.setText("Ink palette");
        label.setTextColor(COLOR_ACCENT_DEEP);
        label.setTextSize(12f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setIncludeFontPadding(false);
        controls.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(20)));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        final int selectedSlot = OverlayPrefs.rippleInkPalette(this);
        for (int index = 0; index < RippleInkPortEngine.paletteCount(); index++) {
            final int slot = index + 1;
            RippleInkPaletteSwatchView swatch = new RippleInkPaletteSwatchView(
                    slot, rippleInkPreviewColor(slot), RIPPLE_INK_PALETTE_NAMES[index],
                    slot == selectedSlot);
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.edit().putInt(OverlayPrefs.RIPPLE_INK_PALETTE, slot).apply();
                    showTab(selectedTab);
                }
            });
            // Share the available row between all eight slots.  The previous fixed 44dp
            // swatches plus 8dp gaps made the last two colours look missing on 360dp layouts.
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1.0f);
            if (index > 0) {
                params.setMargins(dp(2), 0, 0, 0);
            }
            swatches.addView(swatch, params);
        }
        scroller.addView(swatches, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.MATCH_PARENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        controls.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        return controls;
    }

    private Switch compactEffectVariantSwitch(String label, boolean checked) {
        Switch value = new Switch(this);
        value.setText(label);
        value.setTextColor(COLOR_ACCENT_DEEP);
        value.setTextSize(11f);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setIncludeFontPadding(false);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setChecked(checked);
        tintSwitch(value);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            value.setShowText(false);
            value.setSplitTrack(false);
            value.setSwitchMinWidth(dp(34));
        }
        value.setScaleX(COMPACT_EFFECT_SWITCH_SCALE);
        value.setScaleY(COMPACT_EFFECT_SWITCH_SCALE);
        return value;
    }

    private View effectVariantControls(Switch value) {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        controls.setPadding(dp(12), 0, dp(8), dp(3));
        controls.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)));
        return controls;
    }

    private boolean supportsPerEffectHighFrameRate(int effect) {
        return OverlayPrefs.supportsExperimentalNativeRefreshPhysics(effect);
    }

    /**
     * Use the panel capabilities rather than its currently selected refresh rate: power saver
     * may temporarily run a 120/144 Hz phone at 60 Hz, and a Fold's inactive panel may be the
     * one the user unlocks on next.
     */
    private boolean hasInternalHighRefreshDisplay() {
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager == null) {
            return false;
        }
        HashSet<Integer> inspectedDisplayIds = new HashSet<Integer>();
        Display[] displays = displayManager.getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED);
        if (displays == null || displays.length == 0) {
            displays = displayManager.getDisplays();
        }
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        String builtInName = defaultDisplay == null ? null : defaultDisplay.getName();
        for (Display display : displays) {
            boolean builtInPanel = display != null
                    && (display.getDisplayId() == Display.DEFAULT_DISPLAY
                    || (builtInName != null && builtInName.equals(display.getName())));
            if (!builtInPanel
                    || !inspectedDisplayIds.add(Integer.valueOf(display.getDisplayId()))) {
                continue;
            }
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode != null && mode.getRefreshRate() > 60.5f) {
                    return true;
                }
            }
        }
        return false;
    }

    private View perEffectHighFrameRateControls(final int effect) {
        final boolean supportsSpeed =
                OverlayPrefs.supportsExperimentalNativeRefreshPhysicsSpeed(effect);
        final LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(12), 0, dp(8), dp(4));

        final Switch highFrameRate = new Switch(this);
        highFrameRate.setText("HFR");
        highFrameRate.setContentDescription("High frame rate");
        highFrameRate.setTextColor(COLOR_ACCENT_DEEP);
        highFrameRate.setTextSize(11f);
        highFrameRate.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        highFrameRate.setIncludeFontPadding(false);
        highFrameRate.setGravity(Gravity.CENTER_VERTICAL);
        highFrameRate.setPadding(0, 0, 0, 0);
        highFrameRate.setChecked(OverlayPrefs.experimentalNativeRefreshPhysicsEnabled(this,
                effect));
        tintSwitch(highFrameRate);
        registerHighFrameRateSwitch(effect, highFrameRate);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            highFrameRate.setShowText(false);
            highFrameRate.setSplitTrack(false);
            highFrameRate.setSwitchMinWidth(dp(34));
        }
        highFrameRate.setScaleX(COMPACT_EFFECT_SWITCH_SCALE);
        highFrameRate.setScaleY(COMPACT_EFFECT_SWITCH_SCALE);

        if (supportsSpeed) {
            LinearLayout speed = verticalGroup();
            speed.setPadding(0, 0, dp(10), 0);

            final TextView speedValue = new TextView(this);
            speedValue.setTextColor(COLOR_MUTED);
            speedValue.setTextSize(11f);
            speedValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            speedValue.setIncludeFontPadding(false);
            int initialTenths = OverlayPrefs.experimentalNativeRefreshPhysicsSpeedTenths(this,
                    effect);
            speedValue.setText("Speed " + (initialTenths / 10.0f) + "x");
            speed.addView(speedValue);

            final SeekBar slider = new SeekBar(this);
            final boolean[] trackingTouch = new boolean[] {false};
            slider.setMax(10);
            slider.setProgress(initialTenths - 10);
            slider.setContentDescription("High frame rate speed, 1.0x to 2.0x");
            tintSeekBar(slider);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                        boolean fromUser) {
                    int tenths = Math.max(10, Math.min(20, 10 + progress));
                    speedValue.setText("Speed " + (tenths / 10.0f) + "x");
                    if (fromUser && !trackingTouch[0]) {
                        persistExperimentalNativeRefreshSpeed(effect, tenths);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    trackingTouch[0] = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    trackingTouch[0] = false;
                    int tenths = Math.max(10, Math.min(20, 10 + seekBar.getProgress()));
                    // Commit only when the user releases the thumb, so an active renderer is
                    // never recreated repeatedly while the speed is being scrubbed.
                    persistExperimentalNativeRefreshSpeed(effect, tenths);
                }
            });
            LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(26));
            speed.addView(slider, sliderParams);
            controls.addView(speed, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            highFrameRate.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (syncingHighFrameRateSwitches) {
                        return;
                    }
                    syncHighFrameRateSwitches(effect, highFrameRate, isChecked);
                    prefs.edit().putBoolean(
                            OverlayPrefs.experimentalNativeRefreshPhysicsKey(effect),
                            isChecked).apply();
                    slider.setEnabled(isChecked);
                    slider.setAlpha(isChecked ? 1f : 0.42f);
                    speedValue.setAlpha(isChecked ? 1f : 0.42f);
                }
            });
            boolean enabled = highFrameRate.isChecked();
            slider.setEnabled(enabled);
            slider.setAlpha(enabled ? 1f : 0.42f);
            speedValue.setAlpha(enabled ? 1f : 0.42f);
        } else {
            highFrameRate.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (syncingHighFrameRateSwitches) {
                        return;
                    }
                    syncHighFrameRateSwitches(effect, highFrameRate, isChecked);
                    prefs.edit().putBoolean(OverlayPrefs.experimentalNativeRefreshPhysicsKey(effect),
                            isChecked).apply();
                }
            });
        }

        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
        controls.addView(highFrameRate, switchParams);
        return controls;
    }

    private void persistExperimentalNativeRefreshSpeed(int effect, int tenths) {
        prefs.edit().putInt(
                OverlayPrefs.experimentalNativeRefreshPhysicsSpeedTenthsKey(effect),
                Math.max(10, Math.min(20, tenths))).apply();
    }

    private void registerHighFrameRateSwitch(int effect, Switch value) {
        Integer key = Integer.valueOf(effect);
        ArrayList<Switch> switches = highFrameRateSwitches.get(key);
        if (switches == null) {
            switches = new ArrayList<Switch>();
            highFrameRateSwitches.put(key, switches);
        }
        switches.add(value);
    }

    private void syncHighFrameRateSwitches(int effect, Switch source, boolean checked) {
        ArrayList<Switch> switches = highFrameRateSwitches.get(Integer.valueOf(effect));
        if (switches == null || switches.size() < 2) {
            return;
        }
        syncingHighFrameRateSwitches = true;
        try {
            for (Switch candidate : switches) {
                if (candidate != source && candidate.isChecked() != checked) {
                    candidate.setChecked(checked);
                }
            }
        } finally {
            syncingHighFrameRateSwitches = false;
        }
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
        section.addView(sectionTitle("Doodle layout"));
        section.addView(infoText("Move and resize the doodle directly on a lockscreen preview."));
        section.addView(outlineButton("Open visual editor", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityForResult(
                        new Intent(ControlActivity.this, DoodlePositionActivity.class),
                        REQUEST_DOODLE_POSITION);
            }
        }));
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
        section.addView(toggle("Rolling battery percent",
                OverlayPrefs.DEBUG_ROLLING_CHARGE, false));
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
        section.addView(collapsibleHeader("Touch box", touchBoxExpanded,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        touchBoxExpanded = !touchBoxExpanded;
                        showTab(selectedTab, false, 0);
                    }
                }));

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

        if (!touchBoxExpanded) {
            return section;
        }
        section.addView(invertedToggle("Show touch box", OverlayPrefs.DEBUG_TOUCH_TRANSPARENT, true));
        section.addView(toggle("AOD standby touch box", OverlayPrefs.DEBUG_TOUCH_STANDBY, true));
        section.addView(toggle("Three-finger emergency bypass",
                OverlayPrefs.THREE_FINGER_SAFETY_BYPASS_ENABLED, true));
        section.addView(infoText("When enabled, swipe three fingers together inside the touch "
                + "box to remove L.L.E for the current lock cycle and expose the stock "
                + "lockscreen. It rearms after the next screen-off/on cycle."));
        section.addView(outlineButton(FoldDisplayTarget.backgroundProfiles(this).length > 1
                ? "Dual touch box wizard"
                : "Touch box screenshot wizard", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ControlActivity.this, TouchBoxSetupActivity.class);
                intent.putExtra(TouchBoxSetupActivity.EXTRA_START_CAPTURE, true);
                startActivity(intent);
            }
        }));
        section.addView(outlineButton(FoldDisplayTarget.backgroundProfiles(this).length > 1
                ? "Reset active profile touch box"
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
        section.addView(collapsibleHeader("Advanced settings", lockscreenDebugExpanded,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        lockscreenDebugExpanded = !lockscreenDebugExpanded;
                        showTab(selectedTab);
                    }
        }));
        if (lockscreenDebugExpanded) {
            section.addView(setupWizardControls());
            section.addView(exclusiveDisplayModeToggle(
                    "FOLD MODE (Cover + Main)", OverlayPrefs.FOLD_MODE,
                    FoldDisplayTarget.isFoldDevice(this), OverlayPrefs.TABLET_MODE));
            section.addView(exclusiveDisplayModeToggle(
                    "TABLET MODE (portrait + landscape)", OverlayPrefs.TABLET_MODE,
                    FoldDisplayTarget.isTabletDevice(this)
                            && !FoldDisplayTarget.isFoldDevice(this),
                    OverlayPrefs.FOLD_MODE));
            section.addView(infoText("Display profile: "
                    + FoldDisplayTarget.cacheProfileForContext(this)
                    + ". Enabling either mode disables the other."));
            if (FoldDisplayTarget.usesFoldProfiles(this)) {
                section.addView(foldPanelRoutingControls());
            }
            int current = pendingUnlockEffect >= 0
                    ? pendingUnlockEffect : OverlayPrefs.unlockEffect(this);
            section.addView(effectProfilerControls());
            section.addView(customAppBlacklistControls());
            section.addView(batteryDebugControls());
            section.addView(toggle("Media audio output",
                    OverlayPrefs.LLE_AUDIO_ROUTE_MEDIA, false));
            section.addView(infoText("Routes every L.L.E. effect and lock sound through "
                    + "media volume instead of System sounds. This also bypasses the phone's "
                    + "Screen lock/unlock sound switch."));
            section.addView(toggle("Conservative unlock handoff (slow devices)",
                    OverlayPrefs.DEBUG_CONSERVATIVE_UNLOCK_HANDOFF, false));
            section.addView(infoText("Adds extra settling time before opening the PIN screen. "
                    + "Try this only if Android reports delayed or unrecognized touches, or "
                    + "unlocking sometimes needs a second swipe. The PIN screen may appear "
                    + "slightly later."));
            section.addView(toggle("Legacy quick-panel detection (1.0.5.3)",
                    OverlayPrefs.DEBUG_LEGACY_QUICK_PANEL_DETECTION, false));
            TextView legacyQuickPanelWarning = infoText("Compatibility fallback only. "
                    + "Restores the exact 1.0.5.3 quick-panel event and tree detector. "
                    + "On recent or localized SystemUI versions it may leave L.L.E. active "
                    + "over Quick Settings; keep it off unless the default detector fails.");
            legacyQuickPanelWarning.setTextColor(COLOR_ERROR);
            section.addView(legacyQuickPanelWarning);
            section.addView(bootSafetyBypassToggle());
            TextView bootSafetyWarning = infoText("⚠ DANGER — THIS REMOVES YOUR RECOVERY "
                    + "WINDOW. Enable only after L.L.E. has proven stable on this exact device. "
                    + "If an overlay blocks touch at boot, you may need Safe Mode or ADB to "
                    + "disable or uninstall the app.");
            bootSafetyWarning.setTextColor(COLOR_ERROR);
            bootSafetyWarning.setTextSize(15f);
            bootSafetyWarning.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            section.addView(bootSafetyWarning);
        }
        return section;
    }

    private Switch bootSafetyBypassToggle() {
        final Switch toggle = styledToggle("DANGER: run L.L.E during first 2 boot minutes",
                prefs.getBoolean(OverlayPrefs.DEBUG_BYPASS_BOOT_SAFETY, false));
        toggle.setTextColor(COLOR_ERROR);
        toggle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        final boolean[] internalChange = new boolean[]{false};
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView,
                    boolean isChecked) {
                if (internalChange[0]) {
                    return;
                }
                if (!isChecked) {
                    prefs.edit().putBoolean(
                            OverlayPrefs.DEBUG_BYPASS_BOOT_SAFETY, false).apply();
                    return;
                }
                internalChange[0] = true;
                buttonView.setChecked(false);
                internalChange[0] = false;
                new AlertDialog.Builder(ControlActivity.this)
                        .setTitle("Disable boot recovery safety?")
                        .setMessage("L.L.E will be allowed to mount lockscreen overlays "
                                + "immediately after boot. If touch becomes blocked, the normal "
                                + "120-second recovery window will not exist. Continue only if "
                                + "this exact device has already been tested and is stable.")
                        .setNegativeButton("Keep safety", null)
                        .setPositiveButton("I understand — disable safety",
                                new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prefs.edit().putBoolean(
                                        OverlayPrefs.DEBUG_BYPASS_BOOT_SAFETY, true).apply();
                                internalChange[0] = true;
                                buttonView.setChecked(true);
                                internalChange[0] = false;
                            }
                        })
                        .show();
            }
        });
        return toggle;
    }

    private Switch exclusiveDisplayModeToggle(String label, final String key,
            boolean defaultValue, final String otherKey) {
        Switch toggle = styledToggle(label, prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = prefs.edit().putBoolean(key, isChecked);
                if (isChecked) {
                    editor.putBoolean(otherKey, false);
                }
                editor.apply();
                showTab(selectedTab, false, 0);
            }
        });
        return toggle;
    }

    private View customAppBlacklistControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        section.setLayoutParams(params);
        styleInsetPanel(section);
        section.addView(sectionTitle("Custom app blacklist"));
        section.addView(infoText("Hide L.L.E. effects, doodles, and touch input when a "
                + "vendor-specific app appears over the lockscreen. Enter only the package "
                + "name, for example com.example.app. Built-in safety rules cannot be removed."));

        final EditText packageInput = new EditText(this);
        packageInput.setHint("com.example.app");
        packageInput.setSingleLine(true);
        packageInput.setTextColor(COLOR_TEXT);
        packageInput.setHintTextColor(COLOR_MUTED);
        packageInput.setTextSize(14f);
        packageInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        packageInput.setPadding(dp(14), 0, dp(14), 0);
        packageInput.setBackground(solidDrawable(
                Color.WHITE, dp(14), Color.argb(90, 135, 172, 185), dp(1)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        inputParams.setMargins(0, dp(4), 0, dp(4));
        packageInput.setLayoutParams(inputParams);
        section.addView(packageInput);

        section.addView(outlineButton("Add package", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addCustomBlacklistPackage(packageInput.getText().toString());
            }
        }));

        ArrayList<String> packages = new ArrayList<String>(
                OverlayPrefs.userRuntimeBlacklistPackages(this));
        Collections.sort(packages);
        if (packages.isEmpty()) {
            section.addView(infoText("No user-added packages."));
            return section;
        }

        section.addView(sectionLabel("User-added packages"));
        for (int i = 0; i < packages.size(); i++) {
            final String packageName = packages.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(4), dp(6), dp(4));
            row.setBackground(controlRowBackground(false));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
            rowParams.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(rowParams);

            TextView packageLabel = new TextView(this);
            packageLabel.setText(packageName);
            packageLabel.setTextColor(COLOR_TEXT);
            packageLabel.setTextSize(13f);
            packageLabel.setSingleLine(true);
            packageLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(packageLabel, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button remove = outlineButton("Remove", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    removeCustomBlacklistPackage(packageName);
                }
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                    dp(92), dp(42));
            removeParams.setMargins(dp(8), 0, 0, 0);
            remove.setLayoutParams(removeParams);
            row.addView(remove);
            section.addView(row);
        }
        return section;
    }

    private void addCustomBlacklistPackage(String rawPackageName) {
        String packageName = OverlayPrefs.normalizePackageName(rawPackageName);
        if (!OverlayPrefs.isValidPackageName(packageName)) {
            Toast.makeText(this, "Enter a valid package name, such as com.example.app.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (isProtectedCustomBlacklistPackage(packageName)) {
            Toast.makeText(this, "This core system package cannot be blacklisted.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ChargingAccessibilityService.isBuiltInRuntimeBlacklistPackage(packageName)) {
            Toast.makeText(this, "This package is already covered by L.L.E.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Set<String> packages = OverlayPrefs.userRuntimeBlacklistPackages(this);
        if (!packages.add(packageName)) {
            Toast.makeText(this, "This package is already in your blacklist.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        OverlayPrefs.setUserRuntimeBlacklistPackages(this, packages);
        Toast.makeText(this, "Package added to the blacklist.", Toast.LENGTH_SHORT).show();
        showTab(selectedTab);
    }

    private void removeCustomBlacklistPackage(String packageName) {
        Set<String> packages = OverlayPrefs.userRuntimeBlacklistPackages(this);
        if (!packages.remove(packageName)) {
            return;
        }
        OverlayPrefs.setUserRuntimeBlacklistPackages(this, packages);
        Toast.makeText(this, "Package removed from the blacklist.", Toast.LENGTH_SHORT).show();
        showTab(selectedTab);
    }

    private boolean isProtectedCustomBlacklistPackage(String packageName) {
        return "android".equals(packageName)
                || "com.android.systemui".equals(packageName)
                || "com.samsung.android.app.aodservice".equals(packageName)
                || getPackageName().equals(packageName)
                || packageName.startsWith("com.codex.lle");
    }

    private View batteryDebugControls() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        section.setLayoutParams(params);
        styleInsetPanel(section);
        section.addView(sectionTitle("Battery"));

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

    private void updateTouchBoxSummary() {
        if (touchBoxSummary == null) {
            return;
        }
        String[] profiles = FoldDisplayTarget.backgroundProfiles(this);
        if (profiles.length > 1) {
            String activeProfile = FoldDisplayTarget.cacheProfileForContext(this);
            touchBoxSummary.setText(touchBoxProfileSummary(
                            FoldDisplayTarget.profileLabel(profiles[0]), profiles[0])
                    + "\n" + touchBoxProfileSummary(
                            FoldDisplayTarget.profileLabel(profiles[1]), profiles[1])
                    + "\nActive profile: " + activeProfile);
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
            case OverlayPrefs.EFFECT_N4_INK_IN_WATER:
                return Color.rgb(53, 53, 133);
            case OverlayPrefs.EFFECT_S5_POPPING_COLOURS:
                return Color.rgb(123, 206, 92);
            case OverlayPrefs.EFFECT_GOOD_LOCK_POPPING:
                return Color.rgb(255, 119, 99);
            case OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE:
                return Color.rgb(243, 184, 73);
            case OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING:
                return Color.rgb(80, 189, 226);
            case OverlayPrefs.EFFECT_BRILLIANT_RING:
                return Color.rgb(244, 190, 77);
            case OverlayPrefs.EFFECT_BRILLIANT_CUT:
                return Color.rgb(164, 221, 235);
            case OverlayPrefs.EFFECT_TABS_BLIND:
                return Color.rgb(104, 164, 202);
            case OverlayPrefs.EFFECT_WATERCOLOUR:
                return Color.rgb(125, 113, 230);
            case OverlayPrefs.EFFECT_STONE_SKIPPING:
                return Color.rgb(82, 177, 221);
            case OverlayPrefs.EFFECT_MASS_TENSION:
                return Color.rgb(210, 235, 242);
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
                return Color.rgb(235, 111, 102);
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED:
                return Color.rgb(80, 178, 226);
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES:
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP:
                return Color.rgb(94, 210, 209);
            case OverlayPrefs.EFFECT_S4_ABSTRACT_TILES:
                return Color.rgb(239, 157, 64);
            case OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC:
                return Color.rgb(174, 111, 204);
            case OverlayPrefs.EFFECT_SEASONAL_AUTO:
                return seasonAccentColor(resolveDoodlePreviewSeason(
                        SeasonalDoodleView.SEASON_AUTO));
            case OverlayPrefs.EFFECT_SEASONAL_SPRING:
                return seasonAccentColor(SeasonalDoodleView.SEASON_SPRING);
            case OverlayPrefs.EFFECT_SEASONAL_SUMMER:
                return seasonAccentColor(SeasonalDoodleView.SEASON_SUMMER);
            case OverlayPrefs.EFFECT_SEASONAL_AUTUMN:
                return seasonAccentColor(SeasonalDoodleView.SEASON_AUTUMN);
            case OverlayPrefs.EFFECT_SEASONAL_WINTER:
                return seasonAccentColor(SeasonalDoodleView.SEASON_WINTER);
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

    private int seasonIconResId(int season) {
        switch (season) {
            case SeasonalDoodleView.SEASON_SPRING:
                return R.drawable.icon_doodle_seasonal_spring_lle;
            case SeasonalDoodleView.SEASON_SUMMER:
                return R.drawable.icon_doodle_seasonal_summer_lle;
            case SeasonalDoodleView.SEASON_AUTUMN:
                return R.drawable.icon_doodle_seasonal_autumn_lle;
            case SeasonalDoodleView.SEASON_WINTER:
                return R.drawable.icon_doodle_seasonal_winter_lle;
            case SeasonalDoodleView.SEASON_AUTO:
            default:
                return R.drawable.icon_doodle_seasonal_auto_lle;
        }
    }

    private final class GraceSeasonIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int season;
        private final boolean selected;
        private final Drawable iconDrawable;

        GraceSeasonIconView(int season, boolean selected) {
            super(ControlActivity.this);
            this.season = season;
            this.selected = selected;
            this.iconDrawable = getResources().getDrawable(seasonIconResId(season));
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(1.5f);
            RectF bounds = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            float radius = Math.min(getWidth(), getHeight()) * 0.27f;
            float unit = Math.min(bounds.width(), bounds.height());
            if (iconDrawable != null) {
                iconDrawable.setBounds(
                        Math.round(bounds.left),
                        Math.round(bounds.top),
                        Math.round(bounds.right),
                        Math.round(bounds.bottom));
                iconDrawable.draw(canvas);
                drawPreviewAffordance(canvas, bounds, radius, unit);
                return;
            }
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

            drawPreviewAffordance(canvas, bounds, radius, unit);
        }

        private void drawPreviewAffordance(Canvas canvas, RectF bounds, float radius,
                float unit) {
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

    private final class RandomEffectIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean selected;

        RandomEffectIconView(boolean selected) {
            super(ControlActivity.this);
            this.selected = selected;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(1.5f);
            RectF bounds = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            float radius = Math.min(getWidth(), getHeight()) * 0.27f;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(bounds.left, bounds.top,
                    bounds.right, bounds.bottom,
                    new int[] {Color.rgb(111, 214, 202), Color.rgb(87, 129, 205),
                            Color.rgb(119, 76, 177)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            float left = bounds.left + dp(11);
            float right = bounds.right - dp(10);
            float top = bounds.top + dp(15);
            float bottom = bounds.bottom - dp(15);
            Path path = new Path();
            path.moveTo(left, top);
            path.lineTo(left + dp(7), top);
            path.cubicTo(bounds.centerX(), top, bounds.centerX(), bottom,
                    right - dp(4), bottom);
            canvas.drawPath(path, paint);
            canvas.drawLine(right - dp(4), bottom, right, bottom - dp(4), paint);
            canvas.drawLine(right - dp(4), bottom, right, bottom + dp(4), paint);
            path.reset();
            path.moveTo(left, bottom);
            path.lineTo(left + dp(7), bottom);
            path.cubicTo(bounds.centerX(), bottom, bounds.centerX(), top,
                    right - dp(4), top);
            canvas.drawPath(path, paint);
            canvas.drawLine(right - dp(4), top, right, top - dp(4), paint);
            canvas.drawLine(right - dp(4), top, right, top + dp(4), paint);
            paint.setStrokeWidth(dp(selected ? 2f : 1f));
            paint.setColor(selected ? Color.WHITE : Color.argb(105, 255, 255, 255));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private final class RippleInkPaletteSwatchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;

        RippleInkPaletteSwatchView(int slot, int color, String name, boolean selected) {
            super(ControlActivity.this);
            this.color = color;
            setSelected(selected);
            setClickable(true);
            setFocusable(true);
            setContentDescription("N3 Ripple Ink palette " + slot + ", " + name
                    + (selected ? ", selected" : ""));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = Math.max(0f, Math.min(getWidth(), getHeight()) * 0.5f - dp(5));
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(isSelected() ? 3f : 1f));
            paint.setColor(isSelected() ? Color.WHITE : Color.argb(115, 22, 42, 66));
            canvas.drawCircle(cx, cy, radius, paint);
            if (isSelected()) {
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(2.4f));
                paint.setColor(Color.WHITE);
                canvas.drawLine(cx - radius * 0.34f, cy, cx - radius * 0.08f,
                        cy + radius * 0.28f, paint);
                canvas.drawLine(cx - radius * 0.08f, cy + radius * 0.28f,
                        cx + radius * 0.40f, cy - radius * 0.27f, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
            }
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private final class LensFlareModeSwatchView extends View {
        private final Paint swatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable preview;

        LensFlareModeSwatchView(int drawableResId, String name, boolean selected) {
            super(ControlActivity.this);
            preview = getResources().getDrawable(drawableResId);
            setSelected(selected);
            setClickable(true);
            setFocusable(true);
            setContentDescription("Lens Flare style " + name
                    + (selected ? ", selected" : ""));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = Math.max(0f, Math.min(getWidth(), getHeight()) * 0.5f - dp(5));
            float centerX = getWidth() * 0.5f;
            float centerY = getHeight() * 0.5f;
            swatchPaint.setStyle(Paint.Style.FILL);
            swatchPaint.setColor(isSelected() ? Color.rgb(19, 91, 108) : Color.TRANSPARENT);
            canvas.drawCircle(centerX, centerY, radius, swatchPaint);
            if (preview != null) {
                canvas.save();
                Path clip = new Path();
                clip.addCircle(centerX, centerY, radius, Path.Direction.CW);
                canvas.clipPath(clip);
                preview.setBounds(Math.round(centerX - radius), Math.round(centerY - radius),
                        Math.round(centerX + radius), Math.round(centerY + radius));
                preview.draw(canvas);
                canvas.restore();
            }
            swatchPaint.setStyle(Paint.Style.STROKE);
            swatchPaint.setStrokeWidth(dp(isSelected() ? 3f : 1.5f));
            swatchPaint.setColor(isSelected()
                    ? Color.WHITE : Color.argb(180, 22, 42, 66));
            canvas.drawCircle(centerX, centerY, radius, swatchPaint);
            if (isSelected()) {
                swatchPaint.setStrokeCap(Paint.Cap.ROUND);
                swatchPaint.setStrokeWidth(dp(2.4f));
                swatchPaint.setColor(Color.WHITE);
                canvas.drawLine(centerX - radius * 0.34f, centerY,
                        centerX - radius * 0.08f, centerY + radius * 0.28f, swatchPaint);
                canvas.drawLine(centerX - radius * 0.08f, centerY + radius * 0.28f,
                        centerX + radius * 0.40f, centerY - radius * 0.27f, swatchPaint);
                swatchPaint.setStrokeCap(Paint.Cap.BUTT);
            }
            swatchPaint.setStyle(Paint.Style.FILL);
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

        if (OverlayPrefs.isSeasonalUnlockEffect(effect)) {
            drawSeasonalEffectMotif(canvas, paint,
                    resolveDoodlePreviewSeason(
                            OverlayPrefs.seasonForUnlockEffect(effect)),
                    cx, cy, unit);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeCap(Paint.Cap.BUTT);
            return;
        }

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
            case OverlayPrefs.EFFECT_GOOD_LOCK_POPPING:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - unit * 0.20f, cy + unit * 0.13f, unit * 0.14f, paint);
                canvas.drawCircle(cx + unit * 0.14f, cy - unit * 0.16f, unit * 0.10f, paint);
                canvas.drawCircle(cx + unit * 0.22f, cy + unit * 0.22f, unit * 0.07f, paint);
                break;
            case OverlayPrefs.EFFECT_GOOD_LOCK_RECTANGLE:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.055f));
                canvas.drawRect(cx - unit * 0.33f, cy - unit * 0.20f,
                        cx - unit * 0.04f, cy + unit * 0.13f, paint);
                canvas.drawRect(cx + unit * 0.05f, cy - unit * 0.12f,
                        cx + unit * 0.31f, cy + unit * 0.19f, paint);
                break;
            case OverlayPrefs.EFFECT_GOOD_LOCK_BOUNCING:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.05f));
                canvas.drawCircle(cx - unit * 0.20f, cy + unit * 0.14f, unit * 0.13f, paint);
                canvas.drawCircle(cx + unit * 0.17f, cy - unit * 0.13f, unit * 0.11f, paint);
                canvas.drawLine(cx - unit * 0.35f, cy + unit * 0.34f,
                        cx + unit * 0.35f, cy + unit * 0.34f, paint);
                break;
            case OverlayPrefs.EFFECT_BRILLIANT_RING:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.055f));
                canvas.drawCircle(cx - unit * 0.08f, cy + unit * 0.08f,
                        unit * 0.34f, paint);
                canvas.drawCircle(cx + unit * 0.18f, cy - unit * 0.18f,
                        unit * 0.17f, paint);
                canvas.drawCircle(cx + unit * 0.30f, cy - unit * 0.34f,
                        unit * 0.07f, paint);
                break;
            case OverlayPrefs.EFFECT_BRILLIANT_CUT:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.055f));
                Path brilliantCut = new Path();
                brilliantCut.moveTo(cx, rect.top);
                brilliantCut.lineTo(rect.right, cy);
                brilliantCut.lineTo(cx, rect.bottom);
                brilliantCut.lineTo(rect.left, cy);
                brilliantCut.close();
                brilliantCut.moveTo(cx, rect.top);
                brilliantCut.lineTo(cx, rect.bottom);
                brilliantCut.moveTo(rect.left, cy);
                brilliantCut.lineTo(rect.right, cy);
                brilliantCut.moveTo(cx, rect.top);
                brilliantCut.lineTo(rect.right, cy);
                brilliantCut.lineTo(rect.left, cy);
                brilliantCut.close();
                canvas.drawPath(brilliantCut, paint);
                break;
            case OverlayPrefs.EFFECT_TABS_BLIND:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.06f));
                for (int i = 0; i <= 5; i++) {
                    float x = rect.left + (rect.width() * i / 5f);
                    canvas.drawLine(x, rect.top, x, rect.bottom, paint);
                }
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
            case OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES_WIP:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.065f));
                canvas.drawCircle(cx - unit * 0.16f, cy + unit * 0.12f, unit * 0.20f, paint);
                canvas.drawCircle(cx + unit * 0.18f, cy - unit * 0.14f, unit * 0.14f, paint);
                canvas.drawCircle(cx + unit * 0.23f, cy + unit * 0.25f, unit * 0.07f, paint);
                break;
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO_WIP:
            case OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET:
            case OverlayPrefs.EFFECT_S6_WATER_DROPLET_APP_OWNED:
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
            case OverlayPrefs.EFFECT_N4_INK_IN_WATER:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.065f));
                canvas.drawOval(new RectF(cx - unit * 0.42f, cy - unit * 0.18f,
                        cx + unit * 0.42f, cy + unit * 0.18f), paint);
                canvas.drawOval(new RectF(cx - unit * 0.25f, cy - unit * 0.10f,
                        cx + unit * 0.25f, cy + unit * 0.10f), paint);
                break;
            case OverlayPrefs.EFFECT_STONE_SKIPPING:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.055f));
                canvas.drawCircle(cx, cy, unit * 0.38f, paint);
                canvas.drawCircle(cx, cy, unit * 0.24f, paint);
                canvas.drawCircle(cx, cy, unit * 0.10f, paint);
                break;
            case OverlayPrefs.EFFECT_MASS_TENSION:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(1), unit * 0.055f));
                float tensionX = cx + unit * 0.20f;
                float tensionY = cy - unit * 0.14f;
                canvas.drawCircle(cx, cy, unit * 0.38f, paint);
                canvas.drawCircle(tensionX, tensionY, unit * 0.13f, paint);
                canvas.drawLine(cx, cy, tensionX, tensionY, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, unit * 0.055f, paint);
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

    private void drawSeasonalEffectMotif(Canvas canvas, Paint paint, int season,
            float cx, float cy, float unit) {
        paint.setStyle(Paint.Style.FILL);
        if (season == SeasonalDoodleView.SEASON_SPRING) {
            for (int i = 0; i < 5; i++) {
                double angle = -Math.PI * 0.5 + i * Math.PI * 0.4;
                canvas.drawCircle(
                        cx + (float) Math.cos(angle) * unit * 0.20f,
                        cy + (float) Math.sin(angle) * unit * 0.20f,
                        unit * 0.15f,
                        paint);
            }
            int oldColor = paint.getColor();
            paint.setColor(Color.argb(Color.alpha(oldColor), 255, 218, 89));
            canvas.drawCircle(cx, cy, unit * 0.10f, paint);
            paint.setColor(oldColor);
            return;
        }
        if (season == SeasonalDoodleView.SEASON_SUMMER) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(dp(2), unit * 0.055f));
            canvas.drawCircle(cx, cy, unit * 0.17f, paint);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI * 0.25;
                canvas.drawLine(
                        cx + (float) Math.cos(angle) * unit * 0.25f,
                        cy + (float) Math.sin(angle) * unit * 0.25f,
                        cx + (float) Math.cos(angle) * unit * 0.40f,
                        cy + (float) Math.sin(angle) * unit * 0.40f,
                        paint);
            }
            return;
        }
        if (season == SeasonalDoodleView.SEASON_AUTUMN) {
            Path leaf = new Path();
            leaf.moveTo(cx, cy - unit * 0.42f);
            leaf.cubicTo(cx + unit * 0.43f, cy - unit * 0.20f,
                    cx + unit * 0.32f, cy + unit * 0.34f,
                    cx, cy + unit * 0.43f);
            leaf.cubicTo(cx - unit * 0.32f, cy + unit * 0.25f,
                    cx - unit * 0.42f, cy - unit * 0.17f,
                    cx, cy - unit * 0.42f);
            canvas.drawPath(leaf, paint);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(2), unit * 0.055f));
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI / 3.0;
            float dx = (float) Math.cos(angle) * unit * 0.42f;
            float dy = (float) Math.sin(angle) * unit * 0.42f;
            canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint);
        }
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

