package com.codex.lle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * First-run (and manually relaunchable) setup for the shared ARM32/ARM64 app.
 *
 * <p>The launcher activity owns the tiny integration point intentionally kept out of this
 * class: call {@link #shouldLaunch(Context)} and start {@link #createLaunchIntent(Context,
 * boolean)}. A settings entry can relaunch it with {@code manualRelaunch=true}.</p>
 *
 * <p>WallpaperCropActivity contract: it receives the URI and crop configuration constants
 * below, performs the interactive crop, then atomically applies the selected lock/cache mode.
 * It returns {@link Activity#RESULT_OK} only after the operation has really been saved. For
 * {@link #MODE_CACHE_ONLY} it must show the precision acknowledgement requested through
 * {@link #EXTRA_REQUIRE_PRECISE_ACK} before committing.</p>
 */
public class SetupWizardActivity extends Activity {
    public static final String EXTRA_MANUAL_RELAUNCH = "manual_relaunch";
    public static final String EXTRA_START_AT_WALLPAPER = "start_at_wallpaper";
    public static final String EXTRA_SOURCE_URI = "source_uri";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_EFFECT = "effect";
    public static final String EXTRA_TARGET_WIDTH = "target_width";
    public static final String EXTRA_TARGET_HEIGHT = "target_height";
    public static final String EXTRA_REQUIRE_PRECISE_ACK = "require_precise_ack";

    public static final String MODE_AUTOMATIC_SCREENSHOT = "automatic_screenshot";
    public static final String MODE_SET_LOCK_AND_CACHE = "set_lock_and_cache";
    public static final String MODE_CACHE_ONLY = "cache_only";

    private static final String STATE_STEP = "wizard_step";
    private static final String STATE_PENDING_MODE = "pending_wallpaper_mode";
    private static final String STATE_PENDING_PROFILE = "pending_wallpaper_profile";
    private static final String STATE_PENDING_NEXT_PROFILE =
            "pending_wallpaper_next_profile";
    private static final String STATE_WAITING_EXTERNAL_SETTING =
            "waiting_external_setting";
    private static final String WIZARD_PREFS = "setup_wizard_state";
    private static final String PREF_COMPLETED = "completed";
    private static final String PREF_COMPLETED_AT = "completed_at";
    private static final String PREF_STARTED = "started";
    private static final String PREF_WALLPAPER_MODE = "wallpaper_mode";
    private static final String PREF_CURRENT_STEP = "current_step";
    private static final int WIZARD_SCHEMA = 2;
    private static final String PREF_SCHEMA = "schema";

    private static final int REQUEST_PICK_WALLPAPER = 7201;
    private static final int REQUEST_CROP_WALLPAPER = 7202;
    private static final int REQUEST_LOCK_WALLPAPER_ACCESS = 7203;
    private static final int REQUEST_READ_WALLPAPER_STORAGE = 7204;
    private static final int STEP_ACCESSIBILITY = 0;
    private static final int STEP_BATTERY = 1;
    private static final int STEP_WALLPAPER = 2;
    private static final int STEP_FEATURES = 3;
    private static final int STEP_DONE = 4;

    private static final int COLOR_INK = Color.rgb(28, 41, 61);
    private static final int COLOR_MUTED = Color.rgb(98, 111, 129);
    private static final int COLOR_ACCENT = Color.rgb(39, 151, 157);
    private static final int COLOR_ACCENT_DARK = Color.rgb(22, 119, 130);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_OK = Color.rgb(27, 155, 104);
    private static final int COLOR_WARN = Color.rgb(205, 128, 38);

    private FrameLayout contentHost;
    private LinearLayout progressDots;
    private TextView progressLabel;
    private int currentStep = STEP_ACCESSIBILITY;
    private String pendingWallpaperMode = "";
    private String pendingWallpaperProfile = FoldDisplayTarget.PROFILE_SINGLE;
    private String pendingWallpaperNextProfile = "";
    private boolean waitingForExternalSetting;
    private boolean firstResume = true;
    private boolean manualRelaunch;
    private boolean sourceOnlyLaunch;
    private boolean importingPulledWallpaper;

    public static boolean shouldLaunch(Context context) {
        return context != null && !wizardPrefs(context).getBoolean(PREF_COMPLETED, false);
    }

    public static boolean hasCompleted(Context context) {
        return context != null && wizardPrefs(context).getBoolean(PREF_COMPLETED, false);
    }

    public static String selectedWallpaperMode(Context context) {
        if (context == null) {
            return MODE_AUTOMATIC_SCREENSHOT;
        }
        return wizardPrefs(context).getString(
                PREF_WALLPAPER_MODE, MODE_AUTOMATIC_SCREENSHOT);
    }

    public static Intent createLaunchIntent(Context context, boolean manualRelaunch) {
        Intent intent = new Intent(context, SetupWizardActivity.class);
        intent.putExtra(EXTRA_MANUAL_RELAUNCH, manualRelaunch);
        return intent;
    }

    public static Intent createWallpaperLaunchIntent(Context context) {
        Intent intent = createLaunchIntent(context, true);
        intent.putExtra(EXTRA_START_AT_WALLPAPER, true);
        return intent;
    }

    static void rememberWallpaperMode(Context context, String mode) {
        String normalized = mode == null || mode.length() == 0
                ? MODE_AUTOMATIC_SCREENSHOT : mode;
        wizardPrefs(context).edit().putString(PREF_WALLPAPER_MODE, normalized).apply();
    }

    private static SharedPreferences wizardPrefs(Context context) {
        return context.getSharedPreferences(WIZARD_PREFS, Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean manual = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_MANUAL_RELAUNCH, false);
        manualRelaunch = manual;
        sourceOnlyLaunch = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_START_AT_WALLPAPER, false);
        if (!manual && !shouldLaunch(this)) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(STATE_STEP, STEP_ACCESSIBILITY);
            pendingWallpaperMode = savedInstanceState.getString(STATE_PENDING_MODE, "");
            pendingWallpaperProfile = savedInstanceState.getString(
                    STATE_PENDING_PROFILE, FoldDisplayTarget.PROFILE_SINGLE);
            pendingWallpaperNextProfile = savedInstanceState.getString(
                    STATE_PENDING_NEXT_PROFILE, "");
            waitingForExternalSetting = savedInstanceState.getBoolean(
                    STATE_WAITING_EXTERNAL_SETTING, false);
        } else if (getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_START_AT_WALLPAPER, false)) {
            currentStep = STEP_WALLPAPER;
        } else if (!manualRelaunch) {
            currentStep = Math.max(STEP_ACCESSIBILITY, Math.min(STEP_FEATURES,
                    wizardPrefs(this).getInt(PREF_CURRENT_STEP, STEP_ACCESSIBILITY)));
        }
        wizardPrefs(this).edit().putBoolean(PREF_STARTED, true).apply();
        configureWindow();
        buildScene();
        showStep(currentStep, false, 1);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_STEP, currentStep);
        outState.putString(STATE_PENDING_MODE, pendingWallpaperMode);
        outState.putString(STATE_PENDING_PROFILE, pendingWallpaperProfile);
        outState.putString(STATE_PENDING_NEXT_PROFILE, pendingWallpaperNextProfile);
        outState.putBoolean(STATE_WAITING_EXTERNAL_SETTING, waitingForExternalSetting);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            if (!waitingForExternalSetting) {
                return;
            }
        }
        if (waitingForExternalSetting) {
            waitingForExternalSetting = false;
            if (currentStep == STEP_WALLPAPER
                    && MODE_CACHE_ONLY.equals(pendingWallpaperMode)) {
                if (LockscreenWallpaperProbe.hasReadAccess(this)) {
                    importCurrentLockscreenWallpaper();
                } else {
                    fallbackToWallpaperPicker(
                            "Wallpaper access was not granted. Choose the image manually.");
                }
                return;
            }
            if (currentStep == STEP_ACCESSIBILITY && isAccessibilityEnabled()) {
                contentHost.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showStep(STEP_BATTERY, true, 1);
                    }
                }, 260L);
                return;
            }
            if (currentStep == STEP_BATTERY && isBatteryOptimizationIgnored()) {
                contentHost.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showStep(STEP_WALLPAPER, true, 1);
                    }
                }, 260L);
                return;
            }
        }
        if (contentHost != null && currentStep < STEP_DONE) {
            showStep(currentStep, false, 1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WALLPAPER) {
            Uri source = resultCode == RESULT_OK && data != null ? data.getData() : null;
            if (source != null) {
                try {
                    int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(source, flags);
                } catch (Throwable ignored) {
                }
                launchWallpaperCrop(source);
            } else {
                pendingWallpaperMode = "";
                pendingWallpaperProfile = FoldDisplayTarget.PROFILE_SINGLE;
                pendingWallpaperNextProfile = "";
            }
            return;
        }
        if (requestCode == REQUEST_CROP_WALLPAPER) {
            if (resultCode == RESULT_OK) {
                String savedMode = pendingWallpaperMode;
                if (pendingWallpaperNextProfile != null
                        && !pendingWallpaperNextProfile.isEmpty()) {
                    String nextProfile = pendingWallpaperNextProfile;
                    pendingWallpaperNextProfile = "";
                    Toast.makeText(this, "Now choose the " + nextProfile
                                    + " lockscreen wallpaper",
                            Toast.LENGTH_LONG).show();
                    startWallpaperPicker(savedMode, nextProfile);
                    return;
                }
                pendingWallpaperMode = "";
                pendingWallpaperProfile = FoldDisplayTarget.PROFILE_SINGLE;
                completeWallpaperChoice(savedMode);
            } else {
                pendingWallpaperMode = "";
                pendingWallpaperProfile = FoldDisplayTarget.PROFILE_SINGLE;
                pendingWallpaperNextProfile = "";
                Toast.makeText(this,
                        "No changes were saved. You can try again at any time.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_READ_WALLPAPER_STORAGE) {
            return;
        }
        waitingForExternalSetting = false;
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            importCurrentLockscreenWallpaper();
        } else {
            fallbackToWallpaperPicker(
                    "Wallpaper access was not granted. Choose the image manually.");
        }
    }

    @Override
    public void onBackPressed() {
        if (currentStep == STEP_DONE) {
            finish();
        } else if (sourceOnlyLaunch && currentStep == STEP_WALLPAPER) {
            finish();
        } else if (currentStep > STEP_ACCESSIBILITY) {
            showStep(currentStep - 1, true, -1);
        } else {
            // Leaving does not mark setup complete: a genuine first launch will offer it again.
            super.onBackPressed();
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(224, 241, 243));
        window.setNavigationBarColor(Color.rgb(237, 246, 248));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void buildScene() {
        FrameLayout scene = new FrameLayout(this);
        scene.setBackground(gradient(GradientDrawable.Orientation.TL_BR,
                new int[] {
                        Color.rgb(225, 244, 244),
                        Color.rgb(242, 239, 250),
                        Color.rgb(252, 244, 228)
                }, 0, Color.TRANSPARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(20), dp(22), dp(20));
        scene.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        TextView brand = text("L.L.E  /  FIRST SETUP", 12f, COLOR_ACCENT_DARK, true);
        brand.setLetterSpacing(0.13f);
        page.addView(brand);

        LinearLayout progress = new LinearLayout(this);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        progress.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
        progressParams.setMargins(0, dp(5), 0, dp(8));
        page.addView(progress, progressParams);

        progressDots = new LinearLayout(this);
        progressDots.setOrientation(LinearLayout.HORIZONTAL);
        progressDots.setGravity(Gravity.CENTER_VERTICAL);
        progress.addView(progressDots, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(30), dp(6));
            if (i > 0) {
                dotParams.setMargins(dp(7), 0, 0, 0);
            }
            progressDots.addView(dot, dotParams);
        }

        progressLabel = text("1 of 4", 12f, COLOR_MUTED, true);
        progressLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        progress.addView(progressLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        contentHost = new FrameLayout(this);
        page.addView(contentHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(scene);
    }

    private void showStep(final int step, boolean animate, final int direction) {
        currentStep = Math.max(STEP_ACCESSIBILITY, Math.min(STEP_DONE, step));
        if (!manualRelaunch && currentStep < STEP_DONE) {
            wizardPrefs(this).edit().putInt(PREF_CURRENT_STEP, currentStep).apply();
        }
        updateProgress();
        final View next = createStepView(currentStep);
        final View previous = contentHost.getChildCount() == 0
                ? null : contentHost.getChildAt(contentHost.getChildCount() - 1);
        if (!animate || previous == null) {
            contentHost.removeAllViews();
            contentHost.addView(next, fillParams());
            next.setAlpha(0f);
            next.setTranslationY(dp(18));
            next.animate().alpha(1f).translationY(0f).setDuration(340L).start();
            return;
        }
        next.setAlpha(0f);
        next.setTranslationX(direction >= 0 ? dp(42) : -dp(42));
        contentHost.addView(next, fillParams());
        next.animate().alpha(1f).translationX(0f).setDuration(290L).start();
        previous.animate()
                .alpha(0f)
                .translationX(direction >= 0 ? -dp(28) : dp(28))
                .setDuration(230L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        contentHost.removeView(previous);
                    }
                }).start();
    }

    private View createStepView(int step) {
        if (step == STEP_ACCESSIBILITY) {
            return accessibilityStep();
        }
        if (step == STEP_BATTERY) {
            return batteryStep();
        }
        if (step == STEP_WALLPAPER) {
            return wallpaperStep();
        }
        if (step == STEP_FEATURES) {
            return featuresStep();
        }
        return doneStep();
    }

    private View accessibilityStep() {
        final boolean enabled = isAccessibilityEnabled();
        LinearLayout body = stepBody();
        body.addView(kicker("STEP 1", enabled ? "READY" : "REQUIRED",
                enabled ? COLOR_OK : COLOR_ACCENT));
        body.addView(title("Enable the L.L.E service"));
        body.addView(paragraph("Accessibility lets L.L.E detect the lockscreen and show the "
                + "effect at the right time. The service does not read what you type, and you "
                + "can disable it at any time."));
        body.addView(statusCard(enabled ? "Service enabled" : "Service not enabled yet",
                enabled ? "Everything is ready to continue." :
                        "Open Samsung settings and enable L.L.E.", enabled));
        if (isOtherLleAccessibilityEnabled()) {
            body.addView(statusCard("The other L.L.E version is also enabled",
                    "Avoid competing overlays: keep only one L.L.E service enabled while using "
                            + "the app. This wizard will not change that setting.", false));
        }
        Button primary = primaryButton(enabled ? "Continue" : "Open Accessibility settings");
        primary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAccessibilityEnabled()) {
                    showStep(STEP_BATTERY, true, 1);
                    return;
                }
                waitingForExternalSetting = true;
                try {
                    Intent details = new Intent(
                            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
                    details.putExtra(Intent.EXTRA_COMPONENT_NAME, new ComponentName(
                            SetupWizardActivity.this,
                            ChargingAccessibilityService.class));
                    startActivity(details);
                } catch (RuntimeException e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (RuntimeException fallbackError) {
                        waitingForExternalSetting = false;
                        Toast.makeText(SetupWizardActivity.this,
                                "Unable to open Accessibility settings",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        body.addView(primary, actionParams());
        Button later = quietButton("I'll do this later");
        later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStep(STEP_BATTERY, true, 1);
            }
        });
        body.addView(later, quietParams());
        return scroll(body);
    }

    private View batteryStep() {
        final boolean ignored = isBatteryOptimizationIgnored();
        LinearLayout body = stepBody();
        body.addView(kicker("STEP 2", ignored ? "READY" : "RECOMMENDED",
                ignored ? COLOR_OK : COLOR_WARN));
        body.addView(title("Keep the renderer alive"));
        body.addView(paragraph("Allow L.L.E to run without battery restrictions. Otherwise, "
                + "Samsung may suspend the service while the screen is off and make the effect "
                + "unreliable."));
        body.addView(statusCard(ignored ? "Unrestricted battery use" :
                        "Battery optimization is still active",
                ignored ? "The service can prepare even while the screen is off." :
                        "Without this exemption, some features may stop working.", ignored));
        Button primary = primaryButton(ignored ? "Continue" : "Allow unrestricted battery use");
        primary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBatteryOptimizationIgnored()) {
                    showStep(STEP_WALLPAPER, true, 1);
                    return;
                }
                requestBatteryExemption();
            }
        });
        body.addView(primary, actionParams());
        Button later = quietButton("I'll do this later");
        later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBatteryLaterWarning();
            }
        });
        body.addView(later, quietParams());
        return scroll(body);
    }

    private View wallpaperStep() {
        LinearLayout body = stepBody();
        body.addView(kicker("STEP 3", "WALLPAPER", COLOR_ACCENT));
        body.addView(title("How should L.L.E get the wallpaper?"));
        body.addView(paragraph("Choose the source shown behind the effect. You can change it "
                + "later from the main screen."));

        final boolean automaticSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        View automatic = optionCard("01", "Automatic screenshot",
                automaticSupported
                        ? "Use the current capture service and refresh the cache from the lockscreen."
                        : "This option requires Android 11 or newer on this device.",
                "CURRENT", false);
        automatic.setAlpha(automaticSupported ? 1f : 0.55f);
        automatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!automaticSupported) {
                    Toast.makeText(SetupWizardActivity.this,
                            "Automatic capture requires Android 11 or newer",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (FoldDisplayTarget.isFoldDevice(SetupWizardActivity.this)
                        && OverlayPrefs.foldModeEnabled(SetupWizardActivity.this)) {
                    OverlayPrefs.useAutomaticEffectBackgroundForAll(
                            SetupWizardActivity.this, FoldDisplayTarget.PROFILE_COVER);
                    OverlayPrefs.useAutomaticEffectBackgroundForAll(
                            SetupWizardActivity.this, FoldDisplayTarget.PROFILE_MAIN);
                } else {
                    OverlayPrefs.useAutomaticEffectBackgroundForAll(
                            SetupWizardActivity.this, FoldDisplayTarget.PROFILE_SINGLE);
                }
                completeWallpaperChoice(MODE_AUTOMATIC_SCREENSHOT);
            }
        });
        body.addView(automatic, optionParams());

        View setAndCache = optionCard("02", "Set lockscreen + cache (Beta)",
                "Choose a picture, move it, and zoom it. L.L.E will use the same crop as both "
                        + "the lockscreen wallpaper and the renderer's fixed source.",
                "BETA", true);
        setAndCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    Toast.makeText(SetupWizardActivity.this,
                            "Setting the lockscreen wallpaper requires Android 7 or newer",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                WallpaperManager manager = WallpaperManager.getInstance(
                        SetupWizardActivity.this);
                if (!manager.isWallpaperSupported() || !manager.isSetWallpaperAllowed()) {
                    Toast.makeText(SetupWizardActivity.this,
                            "Samsung does not allow this app to change the lockscreen wallpaper",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                startWallpaperPicker(MODE_SET_LOCK_AND_CACHE);
            }
        });
        body.addView(setAndCache, optionParams());

        View exactCache = optionCard("03", "Use the current lockscreen wallpaper (Beta)",
                "Try to read the current lockscreen wallpaper automatically. If Android or "
                        + "the device cannot provide it, choose and align the image manually.",
                "BETA", false);
        exactCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCurrentLockscreenWallpaperImport();
            }
        });
        body.addView(exactCache, optionParams());
        return scroll(body);
    }

    private View featuresStep() {
        LinearLayout body = stepBody();
        body.addView(kicker("STEP 4", "FEATURES", COLOR_ACCENT));
        body.addView(title("What do you want to enable?"));
        body.addView(paragraph("Choose the L.L.E experience you want to start with. You can "
                + "change every option later from the main screen."));

        View doodleOnly = optionCard("01", "Charging doodle only",
                "Show the animated charging doodle, without a lockscreen unlock effect.",
                "DOODLE", false);
        doodleOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFeatureSelection(true, false, false);
            }
        });
        body.addView(doodleOnly, optionParams());

        View lockscreenOnly = optionCard("02", "Lockscreen effect only",
                "Show the selected unlock effect, without the charging doodle.",
                "LOCKSCREEN", false);
        lockscreenOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFeatureSelection(false, true, false);
            }
        });
        body.addView(lockscreenOnly, optionParams());

        View doodleAndLockscreen = optionCard("03",
                "Charging doodle + lockscreen effect",
                "Enable both core experiences while keeping the companion effect off.",
                "BOTH", true);
        doodleAndLockscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFeatureSelection(true, true, false);
            }
        });
        body.addView(doodleAndLockscreen, optionParams());

        View fullExperience = optionCard("04",
                "Doodle + lockscreen + companion effect",
                "Enable the charging doodle, lockscreen effect, and the doodle's companion "
                        + "effect.",
                "FULL", false);
        fullExperience.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFeatureSelection(true, true, true);
            }
        });
        body.addView(fullExperience, optionParams());
        return scroll(body);
    }

    private View doneStep() {
        LinearLayout body = stepBody();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView check = text("\u2713", 38f, Color.WHITE, true);
        check.setGravity(Gravity.CENTER);
        check.setBackground(solid(COLOR_OK, dp(34), Color.TRANSPARENT));
        body.addView(check, new LinearLayout.LayoutParams(dp(68), dp(68)));
        TextView heading = title("Setup complete");
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(20), 0, 0);
        body.addView(heading, titleParams);
        TextView copy = paragraph("L.L.E is ready. You can change these choices at any time "
                + "from the main screen.");
        copy.setGravity(Gravity.CENTER);
        body.addView(copy);
        Button finish = primaryButton("Enter L.L.E");
        finish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SetupWizardActivity.this.finish();
            }
        });
        body.addView(finish, actionParams());
        return scroll(body);
    }

    private void startCurrentLockscreenWallpaperImport() {
        pendingWallpaperMode = MODE_CACHE_ONLY;
        if (!LockscreenWallpaperProbe.isSupported()) {
            fallbackToWallpaperPicker(
                    "Automatic wallpaper reading is unavailable. Choose it manually.");
            return;
        }
        if (LockscreenWallpaperProbe.hasReadAccess(this)) {
            importCurrentLockscreenWallpaper();
            return;
        }

        waitingForExternalSetting = true;
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
                    waitingForExternalSetting = false;
                    fallbackToWallpaperPicker(
                            "Wallpaper access settings are unavailable. Choose it manually.");
                }
            }
            return;
        }
        requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_READ_WALLPAPER_STORAGE);
    }

    private void importCurrentLockscreenWallpaper() {
        if (importingPulledWallpaper) {
            return;
        }
        importingPulledWallpaper = true;
        pendingWallpaperMode = MODE_CACHE_ONLY;
        final boolean fold = FoldDisplayTarget.isFoldDevice(this)
                && OverlayPrefs.foldModeEnabled(this);
        final String[] profiles = fold
                ? new String[] {FoldDisplayTarget.PROFILE_COVER,
                        FoldDisplayTarget.PROFILE_MAIN}
                : new String[] {FoldDisplayTarget.PROFILE_SINGLE};
        Toast.makeText(this, "Reading current lockscreen wallpaper…",
                Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                LockscreenWallpaperProbe.Result[] loaded =
                        new LockscreenWallpaperProbe.Result[profiles.length];
                ManualEffectBackground.ImportResult[] imported =
                        new ManualEffectBackground.ImportResult[profiles.length];
                Throwable failure = null;
                try {
                    for (int i = 0; i < profiles.length; i++) {
                        int[] size = FoldDisplayTarget.displaySizeForProfile(
                                SetupWizardActivity.this, profiles[i]);
                        loaded[i] = LockscreenWallpaperProbe.read(
                                SetupWizardActivity.this, profiles[i], size[0], size[1]);
                        if (loaded[i].bitmap.getWidth() != size[0]
                                || loaded[i].bitmap.getHeight() != size[1]) {
                            throw new IOException(profiles[i] + " wallpaper is "
                                    + loaded[i].bitmap.getWidth() + " x "
                                    + loaded[i].bitmap.getHeight() + ", but that panel needs "
                                    + size[0] + " x " + size[1]);
                        }
                    }
                    int effect = OverlayPrefs.unlockEffect(SetupWizardActivity.this);
                    for (int i = 0; i < profiles.length; i++) {
                        imported[i] = ManualEffectBackground.importPulledLockWallpaper(
                                SetupWizardActivity.this, loaded[i].bitmap, effect,
                                profiles[i], fold
                                ? "Current " + profiles[i] + " lockscreen wallpaper"
                                : "Current lockscreen wallpaper");
                    }
                    for (int i = 0; i < profiles.length; i++) {
                        if (!OverlayPrefs.useImportedEffectBackgroundForAll(
                                SetupWizardActivity.this, profiles[i],
                                imported[i].file, imported[i].displayName,
                                imported[i].width, imported[i].height)) {
                            throw new IOException("LLE could not activate the "
                                    + profiles[i] + " wallpaper");
                        }
                    }
                } catch (Throwable error) {
                    failure = error;
                    Log.w("LLESetup", "Automatic lockscreen wallpaper import failed", error);
                }

                final LockscreenWallpaperProbe.Result[] results = loaded;
                final ManualEffectBackground.ImportResult[] saved = imported;
                final Throwable error = failure;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        importingPulledWallpaper = false;
                        for (LockscreenWallpaperProbe.Result result : results) {
                            if (result != null && result.bitmap != null
                                    && !result.bitmap.isRecycled()) {
                                result.bitmap.recycle();
                            }
                        }
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (error != null || saved.length == 0 || saved[0] == null) {
                            fallbackToWallpaperPicker(
                                    wallpaperImportFailureMessage(error));
                            return;
                        }
                        pendingWallpaperMode = "";
                        Toast.makeText(SetupWizardActivity.this,
                                fold
                                ? "Cover and Main lockscreen wallpapers are active for L.L.E"
                                : "Current lockscreen wallpaper is active for L.L.E",
                                Toast.LENGTH_LONG).show();
                        completeWallpaperChoice(MODE_CACHE_ONLY);
                    }
                });
            }
        }, "LLE-lock-wallpaper-import").start();
    }

    private String wallpaperImportFailureMessage(Throwable error) {
        String detail = error == null ? "" : error.getMessage();
        if (detail != null && detail.toLowerCase().contains("layered or live")) {
            return "Layered wallpaper detected. Samsung protects its composed image, "
                    + "so L.L.E is switching to manual import.";
        }
        if (detail != null && detail.toLowerCase().contains("denied access")) {
            return "Wallpaper access was denied. L.L.E is switching to manual import.";
        }
        return "The current wallpaper could not be imported automatically. "
                + "L.L.E is switching to manual import.";
    }

    private void fallbackToWallpaperPicker(String message) {
        waitingForExternalSetting = false;
        if (message != null && !message.trim().isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        if (FoldDisplayTarget.isFoldDevice(this) && OverlayPrefs.foldModeEnabled(this)) {
            pendingWallpaperNextProfile = FoldDisplayTarget.PROFILE_MAIN;
            Toast.makeText(this, "Choose the Cover wallpaper first, then Main.",
                    Toast.LENGTH_LONG).show();
            startWallpaperPicker(MODE_CACHE_ONLY, FoldDisplayTarget.PROFILE_COVER);
        } else {
            startWallpaperPicker(MODE_CACHE_ONLY);
        }
    }

    private void startWallpaperPicker(String mode) {
        String profile = FoldDisplayTarget.isFoldDevice(this)
                && OverlayPrefs.foldModeEnabled(this)
                ? FoldDisplayTarget.cacheProfileForContext(this)
                : FoldDisplayTarget.PROFILE_SINGLE;
        startWallpaperPicker(mode, profile);
    }

    private void startWallpaperPicker(String mode, String requestedProfile) {
        pendingWallpaperMode = mode;
        pendingWallpaperProfile = FoldDisplayTarget.normalizeProfile(requestedProfile);
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(picker, REQUEST_PICK_WALLPAPER);
        } catch (RuntimeException e) {
            pendingWallpaperMode = "";
            pendingWallpaperProfile = FoldDisplayTarget.PROFILE_SINGLE;
            pendingWallpaperNextProfile = "";
            Toast.makeText(this, "No image picker is available",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void launchWallpaperCrop(Uri source) {
        if (pendingWallpaperMode == null || pendingWallpaperMode.length() == 0) {
            return;
        }
        String profile = FoldDisplayTarget.normalizeProfile(pendingWallpaperProfile);
        int[] targetSize = FoldDisplayTarget.displaySizeForProfile(this, profile);
        int effect = OverlayPrefs.unlockEffect(this);
        Intent crop = new Intent(this, WallpaperCropActivity.class);
        crop.putExtra(EXTRA_SOURCE_URI, source.toString());
        crop.putExtra(EXTRA_MODE, pendingWallpaperMode);
        crop.putExtra(EXTRA_PROFILE, profile);
        crop.putExtra(EXTRA_EFFECT, effect);
        crop.putExtra(EXTRA_TARGET_WIDTH, targetSize[0]);
        crop.putExtra(EXTRA_TARGET_HEIGHT, targetSize[1]);
        crop.putExtra(EXTRA_REQUIRE_PRECISE_ACK,
                MODE_CACHE_ONLY.equals(pendingWallpaperMode));
        crop.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(crop, REQUEST_CROP_WALLPAPER);
        } catch (RuntimeException e) {
            pendingWallpaperMode = "";
            Toast.makeText(this,
                    "The wallpaper editor is not available in this build",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void completeWallpaperChoice(String mode) {
        String normalized = mode == null || mode.length() == 0
                ? MODE_AUTOMATIC_SCREENSHOT : mode;
        wizardPrefs(this).edit().putString(PREF_WALLPAPER_MODE, normalized).apply();
        if (sourceOnlyLaunch) {
            showStep(STEP_DONE, true, 1);
            return;
        }
        showStep(STEP_FEATURES, true, 1);
    }

    private void applyFeatureSelection(boolean doodleEnabled, boolean unlockEnabled,
            boolean companionEnabled) {
        OverlayPrefs.get(this).edit()
                .putBoolean(OverlayPrefs.MASTER_ENABLED, true)
                .putBoolean(OverlayPrefs.SHOW_DOODLE, doodleEnabled)
                .putBoolean(OverlayPrefs.UNLOCK_EFFECT_ENABLED, unlockEnabled)
                .putBoolean(OverlayPrefs.SEASONAL_UNLOCK_PARTNER, companionEnabled)
                .apply();
        wizardPrefs(this).edit()
                .putBoolean(PREF_COMPLETED, true)
                .putLong(PREF_COMPLETED_AT, System.currentTimeMillis())
                .putInt(PREF_SCHEMA, WIZARD_SCHEMA)
                .putInt(PREF_CURRENT_STEP, STEP_DONE)
                .apply();
        showStep(STEP_DONE, true, 1);
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.length() == 0) {
            return false;
        }
        String target = new ComponentName(this,
                ChargingAccessibilityService.class).flattenToString();
        String[] services = enabled.split(":");
        for (int i = 0; i < services.length; i++) {
            if (target.equalsIgnoreCase(services[i])) {
                return true;
            }
        }
        return false;
    }

    private boolean isOtherLleAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.length() == 0) {
            return false;
        }
        String currentPackage = getPackageName();
        String otherPackage = "com.codex.lle64".equals(currentPackage)
                ? "com.codex.lle" : "com.codex.lle64";
        String[] services = enabled.split(":");
        for (int i = 0; i < services.length; i++) {
            ComponentName component = ComponentName.unflattenFromString(services[i]);
            if (component != null
                    && otherPackage.equals(component.getPackageName())
                    && ChargingAccessibilityService.class.getName().equals(
                            component.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBatteryOptimizationIgnored() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryExemption() {
        waitingForExternalSetting = true;
        try {
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            request.setData(Uri.parse("package:" + getPackageName()));
            startActivity(request);
        } catch (RuntimeException directError) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (RuntimeException settingsError) {
                waitingForExternalSetting = false;
                Toast.makeText(this, "Unable to open battery settings",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showBatteryLaterWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Continue without the exemption?")
                .setMessage("Samsung may suspend L.L.E while the screen is off. The effect may "
                        + "start late or not appear until you allow unrestricted battery use.")
                .setNegativeButton("Go back", null)
                .setPositiveButton("Continue anyway", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showStep(STEP_WALLPAPER, true, 1);
                    }
                })
                .show();
    }

    private LinearLayout stepBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(3), dp(8), dp(3), dp(28));
        return body;
    }

    private ScrollView scroll(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(child, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View kicker(String leftText, String rightText, int rightColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = text(leftText, 12f, COLOR_ACCENT_DARK, true);
        left.setLetterSpacing(0.1f);
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = text(rightText, 11f, rightColor, true);
        right.setGravity(Gravity.CENTER);
        right.setPadding(dp(12), dp(6), dp(12), dp(6));
        right.setBackground(solid(Color.argb(26, Color.red(rightColor),
                Color.green(rightColor), Color.blue(rightColor)), dp(14),
                Color.argb(76, Color.red(rightColor), Color.green(rightColor),
                        Color.blue(rightColor))));
        row.addView(right);
        return row;
    }

    private TextView title(String value) {
        TextView title = text(value, 31f, COLOR_INK, true);
        title.setLineSpacing(0f, 0.96f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(20), 0, dp(10));
        title.setLayoutParams(params);
        return title;
    }

    private TextView paragraph(String value) {
        TextView copy = text(value, 16f, COLOR_MUTED, false);
        copy.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(22));
        copy.setLayoutParams(params);
        return copy;
    }

    private View statusCard(String heading, String copy, boolean ok) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        int tone = ok ? COLOR_OK : COLOR_WARN;
        card.setBackground(solid(Color.argb(244, 255, 255, 255), dp(20),
                Color.argb(72, Color.red(tone), Color.green(tone), Color.blue(tone))));
        TextView headingView = text((ok ? "\u2713  " : "!  ") + heading,
                16f, ok ? COLOR_OK : COLOR_INK, true);
        card.addView(headingView);
        TextView copyView = text(copy, 14f, COLOR_MUTED, false);
        copyView.setPadding(0, dp(7), 0, 0);
        card.addView(copyView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(params);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(2));
        }
        return card;
    }

    private View optionCard(String number, String heading, String copy, String badge,
            boolean accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setFocusable(true);
        card.setPadding(dp(18), dp(16), dp(18), dp(17));
        int stroke = accent ? COLOR_ACCENT : Color.rgb(205, 216, 224);
        card.setBackground(solid(accent ? Color.rgb(240, 251, 250) : COLOR_SURFACE,
                dp(22), stroke));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(accent ? 5 : 2));
        }
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView no = text(number, 12f, accent ? COLOR_ACCENT_DARK : COLOR_MUTED, true);
        no.setLetterSpacing(0.12f);
        top.addView(no, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView badgeView = text(badge, 10f, accent ? COLOR_ACCENT_DARK : COLOR_MUTED, true);
        badgeView.setPadding(dp(9), dp(4), dp(9), dp(4));
        badgeView.setBackground(solid(accent ? Color.rgb(216, 243, 241) :
                Color.rgb(242, 246, 248), dp(12), Color.TRANSPARENT));
        top.addView(badgeView);
        card.addView(top);
        TextView headingView = text(heading, 18f, COLOR_INK, true);
        headingView.setPadding(0, dp(9), 0, dp(5));
        card.addView(headingView);
        TextView copyView = text(copy, 14f, COLOR_MUTED, false);
        copyView.setLineSpacing(dp(2), 1f);
        card.addView(copyView);
        return card;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(16f);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setBackground(gradient(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {COLOR_ACCENT_DARK, COLOR_ACCENT}, dp(19), Color.TRANSPARENT));
        button.setStateListAnimator(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dp(5));
        }
        return button;
    }

    private Button quietButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(13f);
        button.setTextColor(COLOR_MUTED);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setStateListAnimator(null);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL));
        return view;
    }

    private void updateProgress() {
        if (progressDots == null) {
            return;
        }
        int selected = Math.min(STEP_FEATURES, currentStep);
        for (int i = 0; i < progressDots.getChildCount(); i++) {
            View dot = progressDots.getChildAt(i);
            boolean active = i <= selected;
            dot.setBackground(solid(active ? COLOR_ACCENT :
                    Color.argb(75, 83, 105, 126), dp(3), Color.TRANSPARENT));
            dot.animate().alpha(active ? 1f : 0.55f).setDuration(220L).start();
        }
        progressLabel.setText(currentStep == STEP_DONE
                ? "Complete" : (currentStep + 1) + " of 4");
    }

    private GradientDrawable solid(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private GradientDrawable gradient(GradientDrawable.Orientation orientation,
            int[] colors, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private FrameLayout.LayoutParams fillParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        params.setMargins(0, dp(4), 0, dp(3));
        return params;
    }

    private LinearLayout.LayoutParams quietParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
    }

    private LinearLayout.LayoutParams optionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
