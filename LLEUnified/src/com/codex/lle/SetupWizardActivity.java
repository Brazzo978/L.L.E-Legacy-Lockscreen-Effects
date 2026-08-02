package com.codex.lle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
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
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
    private static final String STATE_RESTRICTED_SETTINGS_RECOVERY =
            "restricted_settings_recovery";
    private static final String WIZARD_PREFS = "setup_wizard_state";
    private static final String PREF_COMPLETED = "completed";
    private static final String PREF_COMPLETED_AT = "completed_at";
    private static final String PREF_STARTED = "started";
    private static final String PREF_WALLPAPER_MODE = "wallpaper_mode";
    private static final String PREF_CURRENT_STEP = "current_step";
    private static final String PREF_CAPTURE_REQUESTED_AT = "capture_requested_at";
    private static final String SAMSUNG_NIGHT_WALLPAPER_DIM =
            "display_night_theme_wallpaper";
    private static final String SAMSUNG_DYNAMIC_LOCK_WALLPAPER_TYPE =
            "plugin_lock_wallpaper_type";
    private static final String SAMSUNG_DYNAMIC_LOCK_INSTANCE_DATA =
            "key_plugin_lock_instance_data";
    private static final String SAMSUNG_DYNAMIC_LOCK_PACKAGE =
            "com.samsung.android.dynamiclock";
    private static final String SAMSUNG_DYNAMIC_LOCK_SETTINGS_ACTION =
            "dynamic.intent.action.WALLPAPER_SERVICES_ACTIVITY";
    private static final int WIZARD_SCHEMA = 5;
    private static final String PREF_SCHEMA = "schema";

    private static final int REQUEST_PICK_WALLPAPER = 7201;
    private static final int REQUEST_CROP_WALLPAPER = 7202;
    private static final int REQUEST_LOCK_WALLPAPER_ACCESS = 7203;
    private static final int REQUEST_READ_WALLPAPER_STORAGE = 7204;
    private static final int REQUEST_TOUCH_BOX_SETUP = 7205;
    private static final int STEP_ACCESSIBILITY = 0;
    private static final int STEP_BATTERY = 1;
    private static final int STEP_WALLPAPER_DIM = 2;
    private static final int STEP_WALLPAPER = 3;
    private static final int STEP_FEATURES = 4;
    private static final int STEP_PREPARE_SOURCE = 5;
    private static final int STEP_TOUCH_BOX = 6;
    private static final int STEP_DONE = 7;
    private static final int STEP_COUNT = 7;

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
    private boolean restrictedSettingsRecovery;

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
        if (!manual && !wizardPrefs(this).getBoolean(PREF_COMPLETED, false)) {
            // Accessibility can be granted before the user has selected a wallpaper source
            // or feature set. Keep the runtime inert until the final wizard choice.
            OverlayPrefs.get(this).edit()
                    .putBoolean(OverlayPrefs.MASTER_ENABLED, false)
                    .apply();
        }
        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(
                    STATE_STEP, STEP_ACCESSIBILITY);
            pendingWallpaperMode = savedInstanceState.getString(STATE_PENDING_MODE, "");
            pendingWallpaperProfile = savedInstanceState.getString(
                    STATE_PENDING_PROFILE, FoldDisplayTarget.PROFILE_SINGLE);
            pendingWallpaperNextProfile = savedInstanceState.getString(
                    STATE_PENDING_NEXT_PROFILE, "");
            waitingForExternalSetting = savedInstanceState.getBoolean(
                    STATE_WAITING_EXTERNAL_SETTING, false);
            restrictedSettingsRecovery = savedInstanceState.getBoolean(
                    STATE_RESTRICTED_SETTINGS_RECOVERY, false);
        } else if (getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_START_AT_WALLPAPER, false)) {
            currentStep = STEP_WALLPAPER;
        } else if (!manualRelaunch) {
            SharedPreferences state = wizardPrefs(this);
            if (state.getInt(PREF_SCHEMA, 0) < WIZARD_SCHEMA
                    && state.getBoolean(PREF_STARTED, false)) {
                currentStep = STEP_ACCESSIBILITY;
            } else {
                currentStep = Math.max(STEP_ACCESSIBILITY, Math.min(STEP_TOUCH_BOX,
                        state.getInt(PREF_CURRENT_STEP, STEP_ACCESSIBILITY)));
            }
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
        outState.putBoolean(STATE_RESTRICTED_SETTINGS_RECOVERY,
                restrictedSettingsRecovery);
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
            if (currentStep == STEP_WALLPAPER_DIM) {
                if (contentHost != null) {
                    showStep(STEP_WALLPAPER_DIM, false, 1);
                }
                return;
            }
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
            if (currentStep == STEP_ACCESSIBILITY) {
                if (restrictedSettingsRecovery) {
                    if (isRestrictedSettingsAllowed()) {
                        restrictedSettingsRecovery = false;
                        contentHost.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                showStep(STEP_ACCESSIBILITY, true, 1);
                            }
                        }, 260L);
                    } else if (contentHost != null) {
                        showStep(STEP_ACCESSIBILITY, false, 1);
                    }
                    return;
                }
                if (isAccessibilityEnabled()) {
                    contentHost.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showStep(STEP_BATTERY, true, 1);
                        }
                    }, 260L);
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && !isRestrictedSettingsAllowed()) {
                    restrictedSettingsRecovery = true;
                    showStep(STEP_ACCESSIBILITY, true, 1);
                    return;
                }
                return;
            }
            if (currentStep == STEP_BATTERY && isBatteryOptimizationIgnored()) {
                contentHost.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showStep(STEP_WALLPAPER_DIM, true, 1);
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
            return;
        }
        if (requestCode == REQUEST_TOUCH_BOX_SETUP) {
            if (resultCode == RESULT_OK) {
                completeWizard();
            } else {
                Toast.makeText(this,
                        "Touch box was not changed. You can try again or keep the current area.",
                        Toast.LENGTH_SHORT).show();
                showStep(STEP_TOUCH_BOX, false, 1);
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
        } else if (currentStep == STEP_ACCESSIBILITY && restrictedSettingsRecovery) {
            restrictedSettingsRecovery = false;
            showStep(STEP_ACCESSIBILITY, true, -1);
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
        for (int i = 0; i < STEP_COUNT; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(30), dp(6));
            if (i > 0) {
                dotParams.setMargins(dp(7), 0, 0, 0);
            }
            progressDots.addView(dot, dotParams);
        }

        progressLabel = text("1 of " + STEP_COUNT, 12f, COLOR_MUTED, true);
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
            return restrictedSettingsRecovery
                    ? restrictedSettingsStep() : accessibilityStep();
        }
        if (step == STEP_BATTERY) {
            return batteryStep();
        }
        if (step == STEP_WALLPAPER_DIM) {
            return wallpaperDimStep();
        }
        if (step == STEP_WALLPAPER) {
            return wallpaperStep();
        }
        if (step == STEP_FEATURES) {
            return featuresStep();
        }
        if (step == STEP_PREPARE_SOURCE) {
            return prepareSourceStep();
        }
        if (step == STEP_TOUCH_BOX) {
            return touchBoxStep();
        }
        return doneStep();
    }

    private View restrictedSettingsStep() {
        final boolean allowed = isRestrictedSettingsAllowed();
        LinearLayout body = stepBody();
        body.addView(kicker("STEP 1", allowed ? "READY" : "ACTION REQUIRED",
                allowed ? COLOR_OK : COLOR_WARN));
        body.addView(title(allowed
                ? "Accessibility is unlocked"
                : "Allow restricted settings"));
        if (allowed) {
            body.addView(paragraph("Samsung accepted the extra approval. Continue to "
                    + "Accessibility and turn on the " + appLabel() + " service."));
            body.addView(statusCard("Restricted settings allowed",
                    "The blocked switch is now available.", true));
        } else {
            body.addView(paragraph("Samsung needs one extra approval before "
                    + appLabel() + " can be enabled. On the next screen, follow these "
                    + "three steps in order."));
            body.addView(pathStep("1", "Open the top-right menu",
                    "After App info opens, tap \u22ee in the upper-right corner."));
            body.addView(pathStep("2", "Allow restricted settings",
                    "Choose this exact menu item and confirm the warning."));
            body.addView(pathStep("3", "Return to L.L.E",
                    "Press Back. L.L.E will then send you to Accessibility again."));
        }
        Button primary = primaryButton(allowed
                ? "Continue to Accessibility" : "Open App info \u2014 then tap \u22ee");
        primary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRestrictedSettingsAllowed()) {
                    restrictedSettingsRecovery = false;
                    showStep(STEP_ACCESSIBILITY, true, 1);
                    return;
                }
                openAppInfoForRestrictedSettings();
            }
        });
        body.addView(primary, actionParams());
        Button retry = quietButton(allowed
                ? "Back to this guide" : "I allowed it \u2014 check again");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRestrictedSettingsAllowed()) {
                    restrictedSettingsRecovery = false;
                    showStep(STEP_ACCESSIBILITY, true, 1);
                    return;
                }
                Toast.makeText(SetupWizardActivity.this,
                        "Still blocked: open App info, tap \u22ee at the top right, "
                                + "then Allow restricted settings.",
                        Toast.LENGTH_LONG).show();
            }
        });
        body.addView(retry, quietParams());
        return scroll(body);
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
        body.addView(statusCard("RECOVERY SAFETY — READ THIS",
                "If a lockscreen effect ever blocks touch, restart the phone. For the first "
                        + "120 seconds after every boot, L.L.E keeps all overlays and touch "
                        + "listeners disabled so you can turn off its Accessibility service "
                        + "or uninstall the app.", false));
        body.addView(statusCard(enabled ? "Service enabled" : "Service not enabled yet",
                enabled ? "Everything is ready to continue." :
                        "Samsung path: Accessibility \u2192 Installed apps \u2192 "
                                + appLabel() + " \u2192 turn the service on.",
                enabled));
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
                    showStep(STEP_WALLPAPER_DIM, true, 1);
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

    private View wallpaperDimStep() {
        final boolean dimExposed = isSamsungWallpaperDimExposed();
        final boolean dimEnabled = dimExposed && isSamsungWallpaperDimEnabled();
        final boolean dynamicExposed = isSamsungDynamicLockExposed();
        final boolean dynamicEnabled =
                dynamicExposed && isSamsungDynamicLockEnabled();
        final boolean compatibilityRisk = dimEnabled || dynamicEnabled;
        LinearLayout body = stepBody();
        body.addView(kicker("STEP 3",
                compatibilityRisk ? "ACTION STRONGLY RECOMMENDED" : "READY",
                compatibilityRisk ? COLOR_WARN : COLOR_OK));
        body.addView(title("Check Samsung wallpaper compatibility"));
        body.addView(paragraph("L.L.E needs one stable lockscreen image. Samsung wallpaper "
                + "dimming is a protected post-process, while Dynamic Lock Screen can replace "
                + "the image after every lock. Either feature can make the rendered effect "
                + "show a bright, stale, or mismatched wallpaper area."));
        body.addView(statusCard(dimEnabled
                        ? "Wallpaper dimming is enabled"
                        : dimExposed
                                ? "Wallpaper dimming is off"
                                : "No Samsung wallpaper dimming detected",
                dimEnabled
                        ? "Continuing with this enabled can cause bright flashes, mismatched "
                                + "wallpaper layers, or severely broken-looking unlock effects. "
                                + "Open Wallpaper and style and turn it off; L.L.E will verify "
                                + "the setting when you return."
                        : dimExposed
                                ? "Samsung will keep the wallpaper brightness consistent with "
                                        + "the L.L.E renderer."
                                : "This device does not expose Samsung's night wallpaper option.",
                !dimEnabled));
        body.addView(statusCard(dynamicEnabled
                        ? "Dynamic Lock Screen is enabled"
                        : dynamicExposed
                                ? "Dynamic Lock Screen is off"
                                : "No Samsung Dynamic Lock Screen detected",
                dynamicEnabled
                        ? "Samsung is replacing the lockscreen wallpaper after each lock. "
                                + "L.L.E cannot reliably capture that protected image before "
                                + "the same unlock effect starts. Turn Dynamic Lock Screen off; "
                                + "L.L.E will verify the setting when you return."
                        : dynamicExposed
                                ? "The lockscreen image will remain stable between L.L.E "
                                        + "captures."
                                : "This device does not expose Samsung's Dynamic Lock Screen.",
                !dynamicEnabled));

        if (dynamicEnabled) {
            Button dynamicSettings = primaryButton("Turn off Dynamic Lock Screen");
            dynamicSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openDynamicLockSettings();
                }
            });
            body.addView(dynamicSettings, actionParams());
        }
        if (dimEnabled) {
            Button dimSettings = primaryButton("Turn off wallpaper dimming");
            dimSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openWallpaperStyleSettings();
                }
            });
            body.addView(dimSettings, actionParams());
        }

        Button continueButton = compatibilityRisk
                ? quietButton("Continue anyway")
                : primaryButton("Continue");
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean currentDim = isSamsungWallpaperDimEnabled();
                boolean currentDynamic = isSamsungDynamicLockEnabled();
                if (currentDim || currentDynamic) {
                    showWallpaperCompatibilityWarning(currentDim, currentDynamic);
                } else {
                    showStep(STEP_WALLPAPER, true, 1);
                }
            }
        });
        body.addView(continueButton,
                compatibilityRisk ? quietParams() : actionParams());
        return scroll(body);
    }

    private View wallpaperStep() {
        LinearLayout body = stepBody();
        body.addView(kicker("STEP 4", "WALLPAPER", COLOR_ACCENT));
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

        final boolean foldDevice = FoldDisplayTarget.isFoldDevice(this)
                && OverlayPrefs.foldModeEnabled(this);
        View setAndCache = optionCard("02", "Set lockscreen + cache (Beta)",
                foldDevice
                        ? "Unavailable on Fold devices: Samsung routes Cover and Main "
                                + "wallpapers through protected panel-specific APIs."
                        : "Choose a picture, move it, and zoom it. L.L.E will use the same crop "
                                + "as both the lockscreen wallpaper and the renderer's fixed source.",
                "BETA", true);
        setAndCache.setAlpha(foldDevice ? 0.55f : 1f);
        setAndCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (foldDevice) {
                    Toast.makeText(SetupWizardActivity.this,
                            "Use automatic capture or provide the exact wallpaper separately "
                                    + "for Cover and Main",
                            Toast.LENGTH_LONG).show();
                    return;
                }
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
        body.addView(kicker("STEP 5", "FEATURES", COLOR_ACCENT));
        body.addView(title("What do you want to enable?"));
        body.addView(paragraph("Choose the L.L.E experience you want to start with. You can "
                + "change every option later from the main screen."));

        View doodleOnly = optionCard("01", "Charging doodle only",
                "Show the animated charging doodle, without a lockscreen unlock effect.",
                "DOODLE", false);
        doodleOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFeatureSelection(true, false);
            }
        });
        body.addView(doodleOnly, optionParams());

        View lockscreenOnly = optionCard("02", "Lockscreen effect only",
                "Show the selected unlock effect, without the charging doodle.",
                "LOCKSCREEN", false);
        lockscreenOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFeatureSelection(false, true);
            }
        });
        body.addView(lockscreenOnly, optionParams());

        View doodleAndLockscreen = optionCard("03",
                "Charging doodle + lockscreen effect",
                "Show the charging doodle together with any effect selected in the "
                        + "lockscreen effect picker.",
                "BOTH", true);
        doodleAndLockscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFeatureSelection(true, true);
            }
        });
        body.addView(doodleAndLockscreen, optionParams());

        return scroll(body);
    }

    private View prepareSourceStep() {
        final String mode = selectedWallpaperMode(this);
        final boolean automatic = MODE_AUTOMATIC_SCREENSHOT.equals(mode);
        final boolean unlockEnabled = OverlayPrefs.get(this).getBoolean(
                OverlayPrefs.UNLOCK_EFFECT_ENABLED, true);
        final boolean captureReady = !automatic || !unlockEnabled
                || isAutomaticBackgroundReady();

        LinearLayout body = stepBody();
        body.addView(kicker("STEP 6", captureReady ? "READY" : "CAPTURE REQUIRED",
                captureReady ? COLOR_OK : COLOR_WARN));
        if (automatic && unlockEnabled) {
            body.addView(title("Capture the lockscreen once"));
            body.addView(paragraph("Lock the phone, wait on the visible lockscreen for about "
                    + "2–3 seconds, then unlock and return to L.L.E. This gives the selected "
                    + "effect a clean wallpaper source before touch-area calibration."));
            body.addView(statusCard(captureReady
                            ? "Lockscreen screenshot captured"
                            : "Waiting for the lockscreen screenshot",
                    captureReady
                            ? "The source for the active " + activeProfileLabel()
                                    + " display is ready."
                            : "Complete one lock → wait → unlock cycle. This page checks the "
                                    + "saved source automatically when you return.",
                    captureReady));
        } else if (automatic) {
            body.addView(title("Screenshot capture is optional"));
            body.addView(paragraph("You selected the charging doodle without the unlock "
                    + "effect, so no lockscreen-effect screenshot is required right now."));
            body.addView(statusCard("No effect capture required",
                    "If you enable unlock effects later, L.L.E will guide you through a "
                            + "fresh capture from the main screen.",
                    true));
        } else {
            body.addView(title("Wallpaper source ready"));
            body.addView(paragraph("The wallpaper selected and aligned in the previous step "
                    + "is ready. You can continue directly to touch-area calibration."));
            body.addView(statusCard("Fixed wallpaper source saved",
                    MODE_SET_LOCK_AND_CACHE.equals(mode)
                            ? "The same aligned image is used by the lockscreen and L.L.E."
                            : "The exact aligned image is stored in L.L.E's private cache.",
                    true));
        }

        Button primary = primaryButton(captureReady
                ? "Continue to touch box"
                : "I've locked and unlocked — check");
        primary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!automatic || !unlockEnabled || isAutomaticBackgroundReady()) {
                    showStep(STEP_TOUCH_BOX, true, 1);
                    return;
                }
                Toast.makeText(SetupWizardActivity.this,
                        "No screenshot yet. Lock the phone, wait on the lockscreen, then "
                                + "unlock and return here.",
                        Toast.LENGTH_LONG).show();
                showStep(STEP_PREPARE_SOURCE, false, 1);
            }
        });
        body.addView(primary, actionParams());

        if (automatic && unlockEnabled && !captureReady) {
            Button later = quietButton("Continue without screenshot");
            later.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(SetupWizardActivity.this)
                            .setTitle("Continue without a captured wallpaper?")
                            .setMessage("The touch-box editor will request its own lockscreen "
                                    + "capture. Unlock effects may still look wrong until the "
                                    + "normal effect screenshot has been captured.")
                            .setNegativeButton("Go back", null)
                            .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    showStep(STEP_TOUCH_BOX, true, 1);
                                }
                            })
                            .show();
                }
            });
            body.addView(later, quietParams());
        }
        return scroll(body);
    }

    private View touchBoxStep() {
        final boolean fold = FoldDisplayTarget.isFoldDevice(this)
                && OverlayPrefs.foldModeEnabled(this);
        final boolean unlockEnabled = OverlayPrefs.unlockEffectEnabled(this);
        final boolean configured = fold
                ? OverlayPrefs.touchBoxConfigured(this, FoldDisplayTarget.PROFILE_COVER)
                        && OverlayPrefs.touchBoxConfigured(
                                this, FoldDisplayTarget.PROFILE_MAIN)
                : OverlayPrefs.touchBoxConfigured(
                        this, FoldDisplayTarget.PROFILE_SINGLE);

        LinearLayout body = stepBody();
        body.addView(kicker("STEP 7", configured ? "CONFIGURED"
                : (unlockEnabled ? "SET THIS NOW" : "OPTIONAL"),
                configured ? COLOR_OK : COLOR_ACCENT));
        body.addView(title(configured
                ? "Review your touch box"
                : (unlockEnabled ? "Set your unlock touch area"
                        : "Would you like to adjust the touch box?")));
        body.addView(paragraph(fold
                ? "The touch box defines where unlock gestures can activate an effect. "
                        + "The existing dual-panel tool lets you configure independent areas "
                        + "for the Cover and Main displays."
                : "The touch box defines where unlock gestures can activate an effect. "
                        + "The editor uses your prepared lockscreen image so you can position "
                        + "the active area precisely."));
        body.addView(statusCard(configured
                        ? "Touch box already configured"
                        : "Small recovery area is active",
                configured
                        ? (fold
                                ? "Both Fold display profiles already have saved touch areas."
                                : "You can refine the saved area or keep it unchanged.")
                        : "Until you save an area, only a deliberately tiny region near the "
                                + "middle of the screen accepts effect gestures. Open the editor "
                                + "to enlarge it for normal use.",
                configured));

        Button edit = primaryButton(configured
                ? "Review or modify touch box"
                : "Open touch box editor");
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTouchBoxSetup();
            }
        });
        body.addView(edit, actionParams());

        Button keep = quietButton(configured
                ? "Keep current touch box and finish"
                : "Keep tiny recovery box and finish");
        keep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                completeWizard();
            }
        });
        body.addView(keep, quietParams());
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

    private void applyFeatureSelection(boolean doodleEnabled, boolean unlockEnabled) {
        OverlayPrefs.get(this).edit()
                .putBoolean(OverlayPrefs.MASTER_ENABLED, true)
                .putBoolean(OverlayPrefs.SHOW_DOODLE, doodleEnabled)
                .putBoolean(OverlayPrefs.UNLOCK_EFFECT_ENABLED, unlockEnabled)
                .putBoolean(OverlayPrefs.SEASONAL_UNLOCK_PARTNER, false)
                .apply();
        if (unlockEnabled
                && MODE_AUTOMATIC_SCREENSHOT.equals(selectedWallpaperMode(this))) {
            wizardPrefs(this).edit()
                    .putLong(PREF_CAPTURE_REQUESTED_AT, System.currentTimeMillis())
                    .apply();
            OverlayPrefs.requestEffectBackgroundRefresh(this);
        }
        showStep(STEP_PREPARE_SOURCE, true, 1);
    }

    private void launchTouchBoxSetup() {
        Intent intent = new Intent(this, TouchBoxSetupActivity.class);
        intent.putExtra(TouchBoxSetupActivity.EXTRA_START_CAPTURE,
                !hasTouchBoxEditorSource());
        try {
            startActivityForResult(intent, REQUEST_TOUCH_BOX_SETUP);
        } catch (RuntimeException e) {
            Toast.makeText(this,
                    "The touch box editor is not available in this build",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAutomaticBackgroundReady() {
        String profile = activeDisplayProfile();
        int effect = OverlayPrefs.unlockEffect(this);
        File source = OverlayPrefs.effectBackgroundFile(
                this, effect, profile);
        if (!source.exists() || source.length() <= 0L) {
            return false;
        }
        long requestedAt = wizardPrefs(this).getLong(PREF_CAPTURE_REQUESTED_AT, 0L);
        long capturedAt = OverlayPrefs.effectBackgroundLastCapturedAt(
                this, effect, profile);
        return requestedAt <= 0L || capturedAt >= requestedAt;
    }

    private boolean hasTouchBoxEditorSource() {
        String profile = activeDisplayProfile();
        File dedicated = OverlayPrefs.touchBoxScreenshotFile(this, profile);
        if (dedicated.exists() && dedicated.length() > 0L) {
            return true;
        }
        int effect = OverlayPrefs.unlockEffect(this);
        if (OverlayPrefs.importedEffectBackgroundEnabled(this, effect, profile)) {
            File imported = OverlayPrefs.importedEffectBackgroundFile(this, effect, profile);
            if (imported != null && imported.exists() && imported.length() > 0L) {
                return true;
            }
        }
        File automatic = OverlayPrefs.effectBackgroundFile(this, effect, profile);
        return automatic.exists() && automatic.length() > 0L;
    }

    private String activeDisplayProfile() {
        if (FoldDisplayTarget.isFoldDevice(this) && OverlayPrefs.foldModeEnabled(this)) {
            return FoldDisplayTarget.cacheProfileForContext(this);
        }
        return FoldDisplayTarget.PROFILE_SINGLE;
    }

    private String activeProfileLabel() {
        String profile = activeDisplayProfile();
        if (FoldDisplayTarget.PROFILE_COVER.equals(profile)) {
            return "Cover";
        }
        if (FoldDisplayTarget.PROFILE_MAIN.equals(profile)) {
            return "Main";
        }
        return "current";
    }

    private void completeWizard() {
        wizardPrefs(this).edit()
                .putBoolean(PREF_COMPLETED, true)
                .putLong(PREF_COMPLETED_AT, System.currentTimeMillis())
                .putInt(PREF_SCHEMA, WIZARD_SCHEMA)
                .putInt(PREF_CURRENT_STEP, STEP_DONE)
                .remove(PREF_CAPTURE_REQUESTED_AT)
                .apply();
        showStep(STEP_DONE, true, 1);
    }

    private boolean isRestrictedSettingsAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            if (appOps == null) {
                return true;
            }
            int mode = appOps.unsafeCheckOpNoThrow(
                    "android:access_restricted_settings",
                    android.os.Process.myUid(),
                    getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED
                    || mode == AppOpsManager.MODE_FOREGROUND;
        } catch (RuntimeException error) {
            Log.w("LLESetup", "Restricted-settings AppOp unavailable", error);
            return false;
        }
    }

    private void openAppInfoForRestrictedSettings() {
        waitingForExternalSetting = true;
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        details.setData(Uri.parse("package:" + getPackageName()));
        try {
            Toast.makeText(this,
                    "In App info: tap \u22ee at the top right \u2192 "
                            + "Allow restricted settings",
                    Toast.LENGTH_LONG).show();
            startActivity(details);
        } catch (RuntimeException error) {
            waitingForExternalSetting = false;
            Toast.makeText(this, "Unable to open L.L.E app info",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> services =
                manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo service : services) {
            if (service == null || service.getResolveInfo() == null
                    || service.getResolveInfo().serviceInfo == null) {
                continue;
            }
            if (getPackageName().equals(
                    service.getResolveInfo().serviceInfo.packageName)
                    && ChargingAccessibilityService.class.getName().equals(
                            service.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOtherLleAccessibilityEnabled() {
        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        String currentPackage = getPackageName();
        List<AccessibilityServiceInfo> services =
                manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo service : services) {
            if (service == null || service.getResolveInfo() == null
                    || service.getResolveInfo().serviceInfo == null) {
                continue;
            }
            String packageName = service.getResolveInfo().serviceInfo.packageName;
            String className = service.getResolveInfo().serviceInfo.name;
            if (!currentPackage.equals(packageName)
                    && packageName.startsWith("com.codex.lle")
                    && ChargingAccessibilityService.class.getName().equals(
                            className)) {
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

    private boolean isSamsungWallpaperDimExposed() {
        return Settings.System.getString(
                getContentResolver(), SAMSUNG_NIGHT_WALLPAPER_DIM) != null;
    }

    private boolean isSamsungWallpaperDimEnabled() {
        return Settings.System.getInt(
                getContentResolver(), SAMSUNG_NIGHT_WALLPAPER_DIM, 0) != 0;
    }

    private boolean isSamsungDynamicLockExposed() {
        if (!"samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            return false;
        }
        try {
            if (Settings.Secure.getString(
                    getContentResolver(), SAMSUNG_DYNAMIC_LOCK_WALLPAPER_TYPE) != null) {
                return true;
            }
        } catch (RuntimeException error) {
            Log.d("LLESetup", "Dynamic Lock Screen setting is not readable", error);
        }
        try {
            getPackageManager().getApplicationInfo(SAMSUNG_DYNAMIC_LOCK_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private boolean isSamsungDynamicLockEnabled() {
        if (!"samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            return false;
        }
        try {
            String rawType = Settings.Secure.getString(
                    getContentResolver(), SAMSUNG_DYNAMIC_LOCK_WALLPAPER_TYPE);
            if (rawType != null) {
                try {
                    int type = Integer.parseInt(rawType.trim());
                    if (type > 0) {
                        return true;
                    }
                } catch (NumberFormatException error) {
                    Log.d("LLESetup", "Unexpected Dynamic Lock Screen type=" + rawType);
                }
            }
            String instanceData = Settings.Secure.getString(
                    getContentResolver(), SAMSUNG_DYNAMIC_LOCK_INSTANCE_DATA);
            return instanceData != null
                    && (instanceData.contains("\"wallpaper_dynamic\":1")
                            || instanceData.contains("\"wallpaper_dynamic_sub\":1"));
        } catch (RuntimeException error) {
            Log.d("LLESetup", "Unable to verify Dynamic Lock Screen state", error);
            return false;
        }
    }

    private void openDynamicLockSettings() {
        waitingForExternalSetting = true;
        Intent settings = new Intent(SAMSUNG_DYNAMIC_LOCK_SETTINGS_ACTION);
        settings.addCategory(Intent.CATEGORY_DEFAULT);
        settings.setPackage(SAMSUNG_DYNAMIC_LOCK_PACKAGE);
        try {
            startActivity(settings);
            return;
        } catch (RuntimeException directError) {
            Log.w("LLESetup", "Dynamic Lock Screen page unavailable", directError);
        }
        Intent fallback = new Intent("dynamic.intent.action.DLS_SETTINGS");
        fallback.addCategory(Intent.CATEGORY_DEFAULT);
        fallback.setPackage(SAMSUNG_DYNAMIC_LOCK_PACKAGE);
        try {
            startActivity(fallback);
        } catch (RuntimeException fallbackError) {
            Log.w("LLESetup", "Dynamic Lock settings fallback unavailable",
                    fallbackError);
            openWallpaperStyleSettings();
        }
    }

    private void openWallpaperStyleSettings() {
        waitingForExternalSetting = true;
        Intent samsung = new Intent("com.samsung.intent.action.WALLPAPER_SETTING");
        samsung.addCategory(Intent.CATEGORY_DEFAULT);
        samsung.setPackage("com.samsung.android.app.dressroom");
        try {
            startActivity(samsung);
            return;
        } catch (RuntimeException samsungError) {
            Log.w("LLESetup", "Samsung Wallpaper and style page unavailable",
                    samsungError);
        }
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (RuntimeException androidError) {
            waitingForExternalSetting = false;
            Toast.makeText(this, "Unable to open Samsung Settings",
                    Toast.LENGTH_LONG).show();
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
                        showStep(STEP_WALLPAPER_DIM, true, 1);
                    }
                })
                .show();
    }

    private void showWallpaperCompatibilityWarning(
            boolean dimEnabled, boolean dynamicEnabled) {
        StringBuilder message = new StringBuilder();
        if (dimEnabled) {
            message.append("Wallpaper dimming can make the lockscreen and L.L.E layers diverge, "
                    + "causing bright flashes or mismatched wallpaper areas.");
        }
        if (dynamicEnabled) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append("Dynamic Lock Screen replaces the image after each lock. L.L.E "
                    + "cannot reliably capture the new protected wallpaper before the same "
                    + "unlock effect starts, so the previous wallpaper or a broken layer may "
                    + "appear.");
        }
        message.append("\n\nDisable the flagged Samsung feature");
        if (dimEnabled && dynamicEnabled) {
            message.append("s");
        }
        message.append(" and refresh the L.L.E background for reliable effects.");
        new AlertDialog.Builder(this)
                .setTitle("Continue despite the compatibility risk?")
                .setMessage(message.toString())
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

    private View pathStep(String number, String heading, String copy) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(solid(Color.argb(244, 255, 255, 255), dp(18),
                Color.rgb(217, 224, 229)));

        TextView numberView = text(number, 15f, Color.WHITE, true);
        numberView.setGravity(Gravity.CENTER);
        numberView.setBackground(solid(COLOR_ACCENT_DARK, dp(18), Color.TRANSPARENT));
        card.addView(numberView, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout copyColumn = new LinearLayout(this);
        copyColumn.setOrientation(LinearLayout.VERTICAL);
        copyColumn.setPadding(dp(13), 0, 0, 0);
        TextView headingView = text(heading, 16f, COLOR_INK, true);
        copyColumn.addView(headingView);
        TextView copyView = text(copy, 13f, COLOR_MUTED, false);
        copyView.setLineSpacing(dp(2), 1f);
        copyView.setPadding(0, dp(4), 0, 0);
        copyColumn.addView(copyView);
        card.addView(copyColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(1));
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

    private String appLabel() {
        CharSequence label = getApplicationInfo().loadLabel(getPackageManager());
        return label == null || label.length() == 0 ? "L.L.E" : label.toString();
    }

    private void updateProgress() {
        if (progressDots == null) {
            return;
        }
        int selected = Math.min(STEP_TOUCH_BOX, currentStep);
        for (int i = 0; i < progressDots.getChildCount(); i++) {
            View dot = progressDots.getChildAt(i);
            boolean active = i <= selected;
            dot.setBackground(solid(active ? COLOR_ACCENT :
                    Color.argb(75, 83, 105, 126), dp(3), Color.TRANSPARENT));
            dot.animate().alpha(active ? 1f : 0.55f).setDuration(220L).start();
        }
        progressLabel.setText(currentStep == STEP_DONE
                ? "Complete" : (currentStep + 1) + " of " + STEP_COUNT);
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
