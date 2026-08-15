package com.codex.lle;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Creates a user-shareable, non-root diagnostic report without collecting images. */
final class DebugReport {
    private static final String DIRECTORY = "debug-reports";
    private static final String PREFIX = "LLE-debug-";
    private static final String ADVANCED_PREFIX = "LLE-debug-advanced-";
    private static final int MAX_LOGCAT_CHARS = 512 * 1024;
    private static final int MAX_UID_FORMAT_LOGCAT_CHARS = 4 * 1024 * 1024;
    private static final int MAX_REPORT_FILES = 4;
    private static final int MAX_RUNTIME_SIGNATURE_CHARS = 4096;
    private static final Pattern SAFE_RUNTIME_VALUE = Pattern.compile(
            "^[a-zA-Z0-9_.:<>=,| -]{0,512}$");
    private static final Pattern SAFE_RUNTIME_WINDOW_ENTRY = Pattern.compile(
            "^id=-?\\d+,display=-?\\d+,type=-?\\d+,layer=-?\\d+,active=(?:true|false),"
                    + "focused=(?:true|false),titleSignal=(?:shade|pin|none|other),"
                    + "rootPackage=(?:[A-Za-z0-9_.-]+|<non-system>|-),"
                    + "rootClass=(?:[A-Za-z0-9_.$-]+|-)$");
    private static final Pattern SAFE_RUNTIME_NODE_ENTRY = Pattern.compile(
            "^d\\d+:id=(?:-|[A-Za-z0-9_.-]+:id/[A-Za-z0-9_.-]+),"
                    + "class=(?:-|[A-Za-z_$][A-Za-z0-9_.$-]*)$");
    private static final Pattern UID_FORMAT_LOGCAT_LINE = Pattern.compile(
            "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"
                    + "(\\S+)\\s+\\d+\\s+\\d+\\s+[VDIWEAFS]\\s+");
    private static final Pattern LOGCAT_COORDINATE_PAIR = Pattern.compile(
            "(?i)\\b(touch|point|anchor|local|raw|screen|window|center|from|to)="
                    + "-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?");
    private static final Pattern LOGCAT_COORDINATE_BOX = Pattern.compile(
            "(?i)\\bbox=-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?,"
                    + "-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?");
    private static final Pattern LOGCAT_XY_COORDINATES = Pattern.compile(
            "(?i)\\bx=-?\\d+(?:\\.\\d+)?\\s+y=-?\\d+(?:\\.\\d+)?");
    private static final Set<String> SAFE_RUNTIME_SNAPSHOT_FIELDS = new HashSet<String>(
            Arrays.asList(
                    "service_connected", "service_generation", "boot_safety_holding",
                    "boot_safety_remaining_ms", "boot_safety_debug_bypass",
                    "active_runtime_block_window_id", "charging", "service_battery_percent",
                    "display_profile", "display_profile_mode", "display_dimensions",
                    "active_display_id", "active_display_rotation", "active_display_state",
                    "active_display_current_refresh_millihz",
                    "active_display_max_refresh_millihz",
                    "active_display_supported_mode_count",
                    "background_source_type", "background_bitmap_dimensions",
                    "background_cache_bitmap_config",
                    "background_cache_bitmap_allocation_bytes",
                    "background_renderer_borrows_cache", "background_delivery_path",
                    "background_raw_renderer_capable", "background_raw_source_accepted",
                    "background_capture_generation", "background_capture_attempts",
                    "background_capture_attempted_this_session",
                    "background_capture_succeeded_this_session",
                    "background_captured_age_ms", "background_effect",
                    "background_cached_effect", "background_cached_profile",
                    "effect_uses_colormap_current", "effect_supports_no_colormap",
                    "s3_background_mode", "s3_background_source_dimensions",
                    "s3_background_initial_target_dimensions",
                    "s3_background_surface_dimensions", "s3_background_active_dimensions",
                    "s3_background_map_passes", "s3_background_deferred_until_surface",
                    "s3_background_mapping_status", "doodle_attached",
                    "doodle_parked", "effect_attached", "effect_parked", "effect_visible",
                    "affordance_pending", "affordance_shown_this_wake",
                    "affordance_dispatch_queued", "affordance_dispatch_generation",
                    "effect_renderer_type", "effect_renderer_class",
                    "effect_renderer_display_dimensions", "effect_renderer_recreate_pending",
                    "effect_renderer_recreate_reason", "effect_readiness_state",
                    "effect_readiness_detail", "effect_hfr_enabled",
                    "effect_hfr_speed_tenths", "effect_lens_flare_mode",
                    "effect_lens_flare_renderer", "effect_gesture_active",
                    "effect_window_not_touchable", "effect_window_alpha_milli",
                    "effect_window_neutralized_for_handoff", "touch_window_count",
                    "touch_primary_attached", "touch_requested_touchable",
                    "touch_params_not_touchable", "touch_additional_window_count",
                    "touch_resolved_region_count", "touch_resolved_profile",
                    "touch_resolved_dimensions", "touch_cached_while_screen_off",
                    "touch_box_capture_scheduled", "touch_box_capture_in_flight",
                    "touch_box_capture_callback_pending",
                    "buffered_readiness_gesture_active", "lockscreen_session_polling",
                    "blocked_surface_scan_in_flight", "pin_entry_pending",
                    "lock_cycle_safety_bypass_active",
                    "three_finger_safety_bypass_enabled",
                    "pin_entry_surface_visible", "pin_entry_handoff_active",
                    "pin_entry_handoff_attempt", "pin_entry_handoff_callback",
                    "pin_entry_handoff_terminal", "pin_entry_handoff_outcome",
                    "pin_entry_handoff_observed_age_ms", "pin_entry_handoff_interactive",
                    "pin_entry_handoff_keyguard_locked", "pin_entry_handoff_device_locked",
                    "pin_entry_handoff_touch_windows_before",
                    "pin_entry_handoff_touch_windows_after",
                    "pin_entry_handoff_touch_removal_mode",
                    "pin_entry_handoff_touch_removal_result",
                    "pin_entry_handoff_touch_removal_elapsed_ms",
                    "pin_entry_handoff_window_alpha_result",
                    "pin_entry_handoff_window_alpha_elapsed_ms",
                    "pin_entry_handoff_prepare_age_ms",
                    "pin_entry_handoff_swipe_queue_age_ms",
                    "pin_entry_handoff_dispatch_age_ms", "pin_entry_handoff_fail_open",
                    "notification_shade_visible",
                    "notification_shade_diagnostic_age_ms",
                    "notification_shade_diagnostic_reason",
                    "notification_shade_diagnostic_matched",
                    "notification_shade_diagnostic_quality",
                    "notification_shade_diagnostic_windows",
                    "notification_shade_diagnostic_roots",
                    "notification_shade_diagnostic_nodes",
                    "notification_shade_diagnostic_exhausted", "global_actions_visible",
                    "global_actions_age_ms", "background_capture_active",
                    "colour_view_background_dimensions",
                    "colour_view_background_ownership",
                    "colour_view_background_allocation_bytes",
                    "colour_gl_background_dimensions",
                    "colour_gl_background_allocation_bytes", "colour_gl_gpu_ready",
                    "colour_gl_resources_ready", "colour_gl_draw_count",
                    "spark_view_background_dimensions", "spark_view_background_ownership",
                    "spark_view_background_allocation_bytes",
                    "spark_gl_background_dimensions",
                    "spark_gl_background_allocation_bytes", "spark_gl_gpu_ready",
                    "spark_gl_resources_ready", "spark_gl_draw_count",
                    "s6_view_portrait_dimensions", "s6_view_portrait_ownership",
                    "s6_view_portrait_allocation_bytes", "s6_gl_portrait_dimensions",
                    "s6_gl_landscape_dimensions", "s6_gl_background_allocation_bytes",
                    "s6_gl_gpu_ready", "s6_gl_resources_ready", "s6_gl_draw_count"));
    private static final Set<String> RUNTIME_SIGNATURE_FIELDS = new HashSet<String>(
            Arrays.asList(
                    "notification_shade_window_signature",
                    "notification_shade_visible_node_signature",
                    "notification_shade_confirmed_window_signature",
                    "notification_shade_confirmed_node_signature"));

    private DebugReport() {
    }

    static File create(Context context) throws IOException {
        return create(context, false);
    }

    static File createAdvanced(Context context) throws IOException {
        return create(context, true);
    }

    private static File create(Context context, boolean advanced) throws IOException {
        Context appContext = context.getApplicationContext();
        File directory = reportDirectory(appContext);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create debug report directory");
        }
        pruneOldReports(directory);

        File report = new File(directory,
                (advanced ? ADVANCED_PREFIX : PREFIX)
                        + utcTimestamp("yyyyMMdd-HHmmss") + ".txt");
        StringBuilder body = new StringBuilder(64 * 1024);
        appendHeader(body, appContext, advanced);
        appendRuntimeState(body, appContext, advanced);
        appendAudioState(body, appContext);
        appendPreferences(body, appContext, advanced);
        appendInternalFiles(body, appContext, advanced);
        appendLogcat(body, advanced);

        Writer writer = null;
        try {
            writer = new OutputStreamWriter(
                    new FileOutputStream(report), StandardCharsets.UTF_8);
            writer.write(body.toString());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        return report;
    }

    static File reportDirectory(Context context) {
        return new File(context.getCacheDir(), DIRECTORY);
    }

    static boolean isShareableReportName(String name) {
        return name != null
                && name.startsWith(PREFIX)
                && name.endsWith(".txt")
                && name.indexOf('/') < 0
                && name.indexOf('\\') < 0;
    }

    static boolean isAdvancedReportName(String name) {
        return name != null && name.startsWith(ADVANCED_PREFIX) && name.endsWith(".txt");
    }

    private static void appendHeader(StringBuilder body, Context context, boolean advanced) {
        body.append("L.L.E debug report\n");
        body.append("debug_report_schema_version=3\n");
        body.append("debug_report_mode=")
                .append(advanced ? "advanced_unredacted" : "standard_redacted")
                .append('\n');
        if (advanced) {
            body.append("privacy_warning=UNREDACTED: this report may contain notification "
                    + "or accessibility text, app/package names, filenames, paths, imported "
                    + "source references and exact touch coordinates; share only with a "
                    + "trusted recipient\n");
        } else {
            body.append("privacy_notice=diagnostic beta: app UID/PID log messages are included "
                    + "and may contain notification or accessibility text; review before "
                    + "sharing\n");
        }
        body.append("created_utc=").append(utcTimestamp("yyyy-MM-dd'T'HH:mm:ss'Z'"))
                .append('\n');
        body.append("package=").append(context.getPackageName()).append('\n');
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            body.append("version_name=").append(info.versionName).append('\n');
            body.append("version_code=").append(info.versionCode).append('\n');
        } catch (PackageManager.NameNotFoundException error) {
            body.append("version=unavailable\n");
        }
        body.append("build_flavor=")
                .append(EffectAvailability.buildFlavorLabel()).append('\n');
        body.append("legacy_vendor_effects=")
                .append(EffectAvailability.hasLegacyVendorEffects()).append('\n');
        body.append("process_64_bit=")
                .append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && android.os.Process.is64Bit())
                .append('\n');
        body.append("supported_abis=").append(Arrays.toString(Build.SUPPORTED_ABIS))
                .append('\n');
        body.append("manufacturer=").append(Build.MANUFACTURER).append('\n');
        body.append("brand=").append(Build.BRAND).append('\n');
        body.append("model=").append(Build.MODEL).append('\n');
        body.append("device=").append(Build.DEVICE).append('\n');
        body.append("product=").append(Build.PRODUCT).append('\n');
        body.append("android_release=").append(Build.VERSION.RELEASE).append('\n');
        body.append("android_sdk=").append(Build.VERSION.SDK_INT).append('\n');
        body.append("android_security_patch=")
                .append(Build.VERSION.SECURITY_PATCH).append('\n');
        body.append("build_incremental=")
                .append(Build.VERSION.INCREMENTAL).append('\n');
        body.append("build_display=").append(Build.DISPLAY).append('\n');
        body.append("build_fingerprint=").append(Build.FINGERPRINT).append('\n');
        appendPackageVersion(body, context, "systemui", "com.android.systemui");
        body.append('\n');
    }

    private static void appendPackageVersion(
            StringBuilder body, Context context, String label, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            body.append(label).append("_package=").append(packageName).append('\n');
            body.append(label).append("_version_name=")
                    .append(info.versionName == null ? "unknown" : info.versionName)
                    .append('\n');
            body.append(label).append("_version_code=")
                    .append(versionCode).append('\n');
        } catch (PackageManager.NameNotFoundException error) {
            body.append(label).append("_version=unavailable\n");
        } catch (RuntimeException error) {
            body.append(label).append("_version=error:")
                    .append(error.getClass().getSimpleName()).append('\n');
        }
    }

    private static void appendRuntimeState(
            StringBuilder body, Context context, boolean advanced) {
        body.append("[runtime]\n");
        int rawUnlockEffect = OverlayPrefs.rawUnlockEffect(context);
        int resolvedUnlockEffect = OverlayPrefs.unlockEffect(context);
        body.append("unlock_effect_raw=").append(rawUnlockEffect).append('\n');
        body.append("unlock_effect_resolved=").append(resolvedUnlockEffect).append('\n');
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        body.append("interactive=").append(power != null && power.isInteractive()).append('\n');
        body.append("power_save=").append(power != null && power.isPowerSaveMode()).append('\n');
        body.append("battery_optimization_ignored=")
                .append(power != null
                        && power.isIgnoringBatteryOptimizations(context.getPackageName()))
                .append('\n');

        KeyguardManager keyguard =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        body.append("keyguard_locked=")
                .append(keyguard != null && keyguard.isKeyguardLocked()).append('\n');
        body.append("device_locked=")
                .append(keyguard != null && keyguard.isDeviceLocked()).append('\n');
        body.append("accessibility_enabled=").append(isAccessibilityEnabled(context))
                .append('\n');
        body.append("active_display_profile=")
                .append(FoldDisplayTarget.cacheProfileForContext(context)).append('\n');
        String colormapProfile = FoldDisplayTarget.cacheProfileForContext(context);
        body.append("display_profile_mode=")
                .append(FoldDisplayTarget.modeLabel(context)).append('\n');
        body.append("tablet_mode_enabled=")
                .append(OverlayPrefs.tabletModeEnabled(context)).append('\n');
        boolean importedColormap = OverlayPrefs.importedEffectBackgroundEnabled(
                context, resolvedUnlockEffect, colormapProfile);
        boolean colormapDisabled = OverlayPrefs.testerNoColormapModeEnabled(context);
        body.append("active_colormap_source=")
                .append(colormapDisabled ? "disabled"
                        : importedColormap ? "imported" : "automatic").append('\n');
        int importedOriginalWidth = importedColormap
                ? OverlayPrefs.importedEffectBackgroundWidth(
                        context, resolvedUnlockEffect, colormapProfile) : 0;
        int importedOriginalHeight = importedColormap
                ? OverlayPrefs.importedEffectBackgroundHeight(
                        context, resolvedUnlockEffect, colormapProfile) : 0;
        File activeColormapFile = importedColormap
                ? OverlayPrefs.importedEffectBackgroundFile(
                        context, resolvedUnlockEffect, colormapProfile)
                : OverlayPrefs.effectBackgroundFile(
                        context, resolvedUnlockEffect, colormapProfile);
        if (!importedColormap && !Argb8888BitmapStore.isUsable(activeColormapFile)) {
            activeColormapFile = OverlayPrefs.legacyPngEffectBackgroundFile(
                    context, colormapProfile);
        }
        Argb8888BitmapStore.Info bounds = colormapDisabled
                ? null : Argb8888BitmapStore.inspect(activeColormapFile);
        int colormapWidth = bounds == null ? 0 : bounds.width;
        int colormapHeight = bounds == null ? 0 : bounds.height;
        body.append("active_colormap_storage=")
                .append(colormapDisabled ? "none"
                        : Argb8888BitmapStore.isRaw(activeColormapFile)
                                ? "argb8888" : "legacy_or_missing")
                .append('\n');
        long activeColormapFileBytes = activeColormapFile != null
                && activeColormapFile.isFile() ? activeColormapFile.length() : 0L;
        long expectedPayloadBytes = colormapWidth > 0 && colormapHeight > 0
                ? (long) colormapWidth * colormapHeight * 4L : 0L;
        long payloadBytes = bounds == null ? 0L : bounds.payloadBytes;
        body.append("active_colormap_profile=").append(colormapProfile).append('\n');
        body.append("active_colormap_file_bytes=")
                .append(activeColormapFileBytes).append('\n');
        body.append("active_colormap_payload_bytes=").append(payloadBytes).append('\n');
        body.append("active_colormap_expected_payload_bytes=")
                .append(expectedPayloadBytes).append('\n');
        body.append("active_colormap_payload_matches_dimensions=")
                .append(payloadBytes == expectedPayloadBytes && expectedPayloadBytes > 0L)
                .append('\n');
        body.append("active_colormap_file_overhead_bytes=")
                .append(Math.max(0L, activeColormapFileBytes - payloadBytes)).append('\n');
        body.append("active_colormap_file_modified_age_ms=")
                .append(activeColormapFile != null && activeColormapFile.lastModified() > 0L
                        ? Math.max(0L,
                                System.currentTimeMillis() - activeColormapFile.lastModified())
                        : -1L)
                .append('\n');
        long lastCapturedAt = OverlayPrefs.effectBackgroundLastCapturedAt(
                context, resolvedUnlockEffect, colormapProfile);
        body.append("active_colormap_last_capture_age_ms=")
                .append(lastCapturedAt > 0L
                        ? Math.max(0L, System.currentTimeMillis() - lastCapturedAt) : -1L)
                .append('\n');
        if (importedColormap) {
            body.append("original_import_dimensions=")
                    .append(importedOriginalWidth).append('x')
                    .append(importedOriginalHeight).append('\n');
        }
        body.append("active_colormap_dimensions=")
                .append(colormapWidth).append('x').append(colormapHeight).append('\n');
        int[] expectedColormapSize = FoldDisplayTarget.displaySizeForProfile(
                context, colormapProfile);
        int expectedColormapWidth = expectedColormapSize[0];
        int expectedColormapHeight = expectedColormapSize[1];
        body.append("active_colormap_expected_dimensions=")
                .append(expectedColormapWidth).append('x')
                .append(expectedColormapHeight).append('\n');
        body.append("active_colormap_validation=")
                .append(colormapValidationLabel(
                        importedColormap,
                        colormapProfile,
                        colormapWidth,
                        colormapHeight,
                        expectedColormapWidth,
                        expectedColormapHeight))
                .append('\n');

        Intent battery = context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            body.append("battery_percent=")
                    .append(scale > 0 && level >= 0 ? (level * 100 / scale) : -1)
                    .append('\n');
            body.append("battery_status=").append(status).append('\n');
            body.append("battery_plugged=")
                    .append(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
                    .append('\n');
        }

        Runtime runtime = Runtime.getRuntime();
        body.append("heap_used_bytes=")
                .append(runtime.totalMemory() - runtime.freeMemory()).append('\n');
        body.append("heap_total_bytes=").append(runtime.totalMemory()).append('\n');
        body.append("heap_max_bytes=").append(runtime.maxMemory()).append('\n');
        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);
        body.append("process_total_pss_kb=").append(memoryInfo.getTotalPss()).append('\n');
        body.append("process_total_private_dirty_kb=")
                .append(memoryInfo.getTotalPrivateDirty()).append('\n');
        body.append("process_total_shared_dirty_kb=")
                .append(memoryInfo.getTotalSharedDirty()).append('\n');
        body.append("process_total_swappable_pss_kb=")
                .append(memoryInfo.getTotalSwappablePss()).append('\n');
        appendMemoryStat(body, memoryInfo, "process_java_heap_pss_kb", "summary.java-heap");
        appendMemoryStat(body, memoryInfo, "process_native_heap_pss_kb", "summary.native-heap");
        appendMemoryStat(body, memoryInfo, "process_graphics_pss_kb", "summary.graphics");
        appendMemoryStat(body, memoryInfo, "process_code_pss_kb", "summary.code");
        appendMemoryStat(body, memoryInfo, "process_stack_pss_kb", "summary.stack");
        appendMemoryStat(body, memoryInfo, "process_private_other_pss_kb",
                "summary.private-other");
        appendMemoryStat(body, memoryInfo, "process_system_pss_kb", "summary.system");
        body.append("native_heap_size_bytes=").append(Debug.getNativeHeapSize()).append('\n');
        body.append("native_heap_allocated_bytes=")
                .append(Debug.getNativeHeapAllocatedSize()).append('\n');
        body.append("native_heap_free_bytes=")
                .append(Debug.getNativeHeapFreeSize()).append('\n');
        appendRuntimeSnapshot(body, ChargingAccessibilityService.debugRuntimeSnapshot(), advanced);
        body.append('\n');
    }

    static void appendRuntimeSnapshot(
            StringBuilder body, String snapshot, boolean advanced) {
        if (!advanced) {
            appendSanitizedRuntimeSnapshot(body, snapshot);
            return;
        }
        body.append("runtime_snapshot_schema_version=2\n");
        body.append("runtime_snapshot_filter=none\n");
        if (snapshot == null || snapshot.length() == 0) {
            body.append("service_snapshot=unavailable\n");
        } else {
            body.append(snapshot);
            if (snapshot.charAt(snapshot.length() - 1) != '\n') {
                body.append('\n');
            }
        }
        body.append("runtime_snapshot_omitted_fields=0\n");
    }

    private static void appendMemoryStat(StringBuilder body, Debug.MemoryInfo memoryInfo,
            String reportKey, String memoryKey) {
        String value = memoryInfo.getMemoryStat(memoryKey);
        body.append(reportKey).append('=')
                .append(value == null || !value.matches("\\d+") ? "-1" : value)
                .append('\n');
    }

    private static String colormapValidationLabel(
            boolean imported, String profile, int bitmapWidth, int bitmapHeight,
            int targetWidth, int targetHeight) {
        if (imported) {
            return "imported_not_size_gated";
        }
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            return "missing";
        }
        if (!FoldDisplayTarget.bitmapMatches(
                profile, bitmapWidth, bitmapHeight, targetWidth, targetHeight)) {
            return "invalid_profile_shape";
        }
        if (!ChargingAccessibilityService.automaticProfileScreenshotResolutionMatches(
                profile, bitmapWidth, bitmapHeight, targetWidth, targetHeight)) {
            return "invalid_low_resolution";
        }
        return "valid";
    }

    /**
     * The service snapshot is intentionally structured, but it is still fed by SystemUI.
     * Keep only its known diagnostic fields and categorise package/blacklist values so a
     * future free-form field cannot accidentally turn a shareable report into UI telemetry.
     */
    static void appendSanitizedRuntimeSnapshot(StringBuilder body, String snapshot) {
        body.append("runtime_snapshot_schema_version=2\n");
        if (snapshot == null || snapshot.length() == 0) {
            body.append("service_snapshot=unavailable\n");
            body.append("runtime_snapshot_omitted_fields=0\n");
            return;
        }
        int omitted = 0;
        String[] lines = snapshot.split("\\n");
        for (String line : lines) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                omitted++;
                continue;
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if ("last_window_package".equals(key)
                    || "active_runtime_block_package".equals(key)) {
                body.append(key).append('=').append(packageCategory(value)).append('\n');
            } else if ("custom_blacklist_packages".equals(key)) {
                body.append("custom_blacklist_configured=")
                        .append(value.length() > 2).append('\n');
            } else if ("notification_shade_diagnostic_reason".equals(key)) {
                String reason = sanitizeInternalReason(value);
                if (reason == null) {
                    omitted++;
                } else {
                    body.append(key).append('=').append(reason).append('\n');
                }
            } else if (RUNTIME_SIGNATURE_FIELDS.contains(key)) {
                String signature = sanitizeRuntimeSignature(key, value);
                if (signature == null) {
                    omitted++;
                } else {
                    body.append(key).append('=').append(signature).append('\n');
                }
            } else if (SAFE_RUNTIME_SNAPSHOT_FIELDS.contains(key)
                    && SAFE_RUNTIME_VALUE.matcher(value).matches()
                    && !containsPrivateReference(value)) {
                body.append(key).append('=').append(value).append('\n');
            } else {
                omitted++;
            }
        }
        body.append("runtime_snapshot_omitted_fields=").append(omitted).append('\n');
    }

    static String sanitizeInternalReason(String value) {
        if (value == null || value.length() == 0 || value.length() > 256
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.contains("://")) {
            return null;
        }
        StringBuilder safe = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)
                    || character == '_' || character == '-' || character == '.'
                    || character == ':' || character == '<' || character == '>') {
                safe.append(character);
            } else {
                safe.append('_');
            }
        }
        return safe.toString();
    }

    private static String packageCategory(String packageName) {
        if (packageName == null || "<none>".equals(packageName)) {
            return "none";
        }
        if ("com.android.systemui".equals(packageName)) {
            return "systemui";
        }
        if ("com.samsung.android.app.aodservice".equals(packageName)) {
            return "aod";
        }
        return "other";
    }

    private static String sanitizeRuntimeSignature(String key, String value) {
        if (value == null) {
            return null;
        }
        if ("<none>".equals(value) || "unknown".equals(value)) {
            return value;
        }
        boolean windowSignature = key.contains("window_signature");
        Pattern entryPattern = windowSignature
                ? SAFE_RUNTIME_WINDOW_ENTRY : SAFE_RUNTIME_NODE_ENTRY;
        StringBuilder sanitized = new StringBuilder(
                Math.min(value.length(), MAX_RUNTIME_SIGNATURE_CHARS));
        String[] entries = value.split(" \\| ", -1);
        for (String entry : entries) {
            if (!entryPattern.matcher(entry).matches()) {
                return null;
            }
            String safeEntry = windowSignature
                    ? entry : entry.replace(":id/", ":id_");
            int delimiterLength = sanitized.length() == 0 ? 0 : 3;
            if (sanitized.length() + delimiterLength + safeEntry.length()
                    > MAX_RUNTIME_SIGNATURE_CHARS) {
                if (sanitized.length() == 0) {
                    return null;
                }
                sanitized.append(" | <truncated>");
                break;
            }
            if (sanitized.length() > 0) {
                sanitized.append(" | ");
            }
            sanitized.append(safeEntry);
        }
        return sanitized.length() == 0 ? null : sanitized.toString();
    }

    private static boolean isAccessibilityEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName component =
                new ComponentName(context, ChargingAccessibilityService.class);
        return enabled.contains(component.flattenToShortString())
                || enabled.contains(component.flattenToString());
    }

    private static void appendAudioState(StringBuilder body, Context context) {
        body.append("[audio]\n");
        AudioManager audio =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            body.append("audio_manager=unavailable\n\n");
            return;
        }
        int ringerMode = audio.getRingerMode();
        body.append("ringer_mode=").append(ringerModeLabel(ringerMode)).append('\n');
        body.append("ringer_mode_value=").append(ringerMode).append('\n');
        body.append("audio_mode=").append(audio.getMode()).append('\n');
        body.append("lle_audio_route=").append(EffectAudio.routeLabel(context)).append('\n');
        body.append("lle_audio_route_stream=")
                .append(EffectAudio.streamType(context)).append('\n');
        body.append("system_stream_volume=")
                .append(audio.getStreamVolume(AudioManager.STREAM_SYSTEM)).append('\n');
        body.append("system_stream_max_volume=")
                .append(audio.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)).append('\n');
        body.append("system_stream_muted=")
                .append(audio.isStreamMute(AudioManager.STREAM_SYSTEM)).append('\n');
        body.append("music_stream_volume=")
                .append(audio.getStreamVolume(AudioManager.STREAM_MUSIC)).append('\n');
        body.append("music_stream_max_volume=")
                .append(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).append('\n');
        try {
            int lockscreenSounds = Settings.System.getInt(
                    context.getContentResolver(), "lockscreen_sounds_enabled", 1);
            body.append("system_lockscreen_sounds_enabled=")
                    .append(lockscreenSounds != 0).append('\n');
        } catch (RuntimeException error) {
            body.append("system_lockscreen_sounds_enabled=unavailable:")
                    .append(error.getClass().getSimpleName()).append('\n');
        }
        SharedPreferences preferences = OverlayPrefs.get(context);
        body.append("lle_effect_sounds_enabled=")
                .append(preferences.getBoolean(
                        OverlayPrefs.UNLOCK_EFFECT_SOUND_ENABLED, true)).append('\n');
        try {
            body.append("lle_effect_sounds_allowed_now=")
                    .append(OverlayPrefs.unlockEffectSoundAllowedNow(context)).append('\n');
        } catch (RuntimeException error) {
            body.append("lle_effect_sounds_allowed_now=unavailable:")
                    .append(error.getClass().getSimpleName()).append('\n');
        }
        body.append("lle_lock_sound_enabled=")
                .append(OverlayPrefs.lockSoundEnabled(context)).append('\n');
        body.append("lle_lock_sound_allowed_now=")
                .append(OverlayPrefs.lockscreenLockSoundAllowedNow(context)).append('\n');
        body.append('\n');
    }

    private static String ringerModeLabel(int mode) {
        switch (mode) {
            case AudioManager.RINGER_MODE_SILENT:
                return "silent";
            case AudioManager.RINGER_MODE_VIBRATE:
                return "vibrate";
            case AudioManager.RINGER_MODE_NORMAL:
                return "normal";
            default:
                return "unknown";
        }
    }

    private static void appendPreferences(
            StringBuilder body, Context context, boolean advanced) {
        body.append("[preferences]\n");
        body.append("filter=").append(advanced ? "none_except_credentials" : "privacy_safe")
                .append('\n');
        SharedPreferences preferences = OverlayPrefs.get(context);
        Map<String, ?> values = new TreeMap<String, Object>(preferences.getAll());
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            body.append(key).append('=');
            body.append((advanced ? isCredentialPreference(key, value)
                    : isSensitivePreference(key, value))
                    ? "<redacted>" : String.valueOf(value));
            body.append('\n');
        }
        body.append('\n');
    }

    private static boolean isCredentialPreference(String key, Object value) {
        String lowerKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (lowerKey.contains("password") || lowerKey.contains("token")
                || lowerKey.contains("secret") || lowerKey.contains("keystore")
                || lowerKey.contains("p12")) {
            return true;
        }
        if (!(value instanceof String)) {
            if (value instanceof Set) {
                for (Object item : (Set<?>) value) {
                    String lowerItem = String.valueOf(item).toLowerCase(Locale.ROOT);
                    if (lowerItem.contains(".p12") || lowerItem.contains(".keys")) {
                        return true;
                    }
                }
            }
            return false;
        }
        String lowerValue = String.valueOf(value).toLowerCase(Locale.ROOT);
        return lowerValue.contains(".p12") || lowerValue.contains(".keys");
    }

    private static boolean isSensitivePreference(String key, Object value) {
        String lowerKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (lowerKey.contains("uri")
                || lowerKey.contains("path")
                || lowerKey.contains("file")
                || lowerKey.contains("password")
                || lowerKey.contains("token")
                || lowerKey.contains("label")
                || lowerKey.contains("package")
                || lowerKey.contains("blacklist")
                || lowerKey.contains("error")) {
            return true;
        }
        // SharedPreferences strings and sets can contain imported filenames, arbitrary app
        // identifiers, or old diagnostic text. Numeric/boolean settings retain the useful
        // configuration state without exporting any free-form value.
        if (value instanceof String || value instanceof Set) {
            return true;
        }
        return false;
    }

    private static void appendInternalFiles(
            StringBuilder body, Context context, boolean advanced) {
        body.append("[internal_files]\n");
        File[] files = context.getFilesDir().listFiles();
        if (files == null || files.length == 0) {
            body.append("none\n\n");
            return;
        }
        int fileCount = 0;
        int directoryCount = 0;
        long fileBytes = 0L;
        for (File file : files) {
            if (file.isFile()) {
                fileCount++;
                fileBytes += Math.max(0L, file.length());
            } else if (file.isDirectory()) {
                directoryCount++;
            }
        }
        body.append("file_count=").append(fileCount).append('\n');
        body.append("directory_count=").append(directoryCount).append('\n');
        body.append("file_bytes_total=").append(fileBytes).append('\n');
        if (advanced) {
            body.append("file_listing=unredacted_names_no_contents\n");
            Arrays.sort(files);
            for (File file : files) {
                body.append(file.isDirectory() ? "directory" : "file")
                        .append('=')
                        .append(file.getAbsolutePath())
                        .append(",bytes=")
                        .append(file.isFile() ? Math.max(0L, file.length()) : 0L)
                        .append(",modified_ms=")
                        .append(Math.max(0L, file.lastModified()))
                        .append('\n');
            }
        }
        body.append('\n');
    }

    private static void appendLogcat(StringBuilder body, boolean advanced) {
        body.append("[app_uid_logcat]\n");
        String output = captureLogcat("--uid=" + android.os.Process.myUid());
        if (selectorUnsupported(output, "--uid")) {
            body.append("filter_fallback=uid_format (uid selector unsupported)\n");
            output = captureUidFormattedLogcat(android.os.Process.myUid());
            if (uidFormatUnsupported(output)) {
                body.append("filter_fallback_secondary=pid (uid format unsupported)\n");
                output = captureLogcat("--pid=" + android.os.Process.myPid());
            }
        } else {
            body.append("filter=uid\n");
        }
        String reportOutput = output == null || advanced
                ? output : redactLogcatCoordinates(output);
        body.append("message_content=")
                .append(advanced ? "app_logcat_unredacted" : "app_logcat_coordinates_redacted")
                .append('\n');
        body.append("captured_chars=")
                .append(reportOutput == null ? 0 : reportOutput.length())
                .append('\n');
        body.append("captured_lines=").append(lineCount(reportOutput)).append('\n');
        body.append("truncated=")
                .append(reportOutput != null && reportOutput.contains("<logcat truncated>"))
                .append('\n');
        body.append(reportOutput == null ? "unavailable=null\n" : reportOutput);
    }

    private static int lineCount(String value) {
        if (value == null || value.length() == 0) {
            return 0;
        }
        int lines = 1;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\n' && index + 1 < value.length()) {
                lines++;
            }
        }
        return lines;
    }

    static String redactLogcatCoordinates(String output) {
        if (output == null || output.length() == 0) {
            return output;
        }
        String redacted = LOGCAT_COORDINATE_PAIR.matcher(output)
                .replaceAll("$1=<redacted>");
        redacted = LOGCAT_COORDINATE_BOX.matcher(redacted)
                .replaceAll("box=<redacted>");
        return LOGCAT_XY_COORDINATES.matcher(redacted)
                .replaceAll("x=<redacted> y=<redacted>");
    }

    private static String captureLogcat(String selector) {
        return captureLogcat(
                MAX_LOGCAT_CHARS,
                "-d", "-v", "threadtime", "-t", "2000", selector);
    }

    private static String captureUidFormattedLogcat(int uid) {
        String raw = captureLogcat(
                MAX_UID_FORMAT_LOGCAT_CHARS,
                "-d", "-v", "uid", "-v", "threadtime", "-t", "4000");
        if (uidFormatUnsupported(raw)) {
            return raw;
        }
        return filterUidFormattedLogcat(raw, uid);
    }

    static String filterUidFormattedLogcat(String raw, int uid) {
        String numericUid = Integer.toString(uid);
        int userId = uid / 100000;
        int appId = uid % 100000;
        String appAlias = appId >= 10000 && appId <= 19999
                ? "u" + userId + "_a" + (appId - 10000) : "";
        String compactAppAlias = appAlias.length() == 0 ? "" : appAlias.replace("_", "");
        StringBuilder filtered = new StringBuilder(64 * 1024);
        int matchedLines = 0;
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            java.util.regex.Matcher matcher = UID_FORMAT_LOGCAT_LINE.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String lineUid = matcher.group(1);
            if (!numericUid.equals(lineUid)
                    && !appAlias.equals(lineUid)
                    && !compactAppAlias.equals(lineUid)) {
                continue;
            }
            if (filtered.length() + line.length() + 1 > MAX_LOGCAT_CHARS) {
                filtered.append("<logcat truncated>\n");
                break;
            }
            filtered.append(line).append('\n');
            matchedLines++;
        }
        filtered.insert(0, "uid_format_matching_lines=" + matchedLines + "\n");
        return filtered.toString();
    }

    private static String captureLogcat(int maxChars, String... arguments) {
        Process process = null;
        BufferedReader reader = null;
        StringBuilder output = new StringBuilder(64 * 1024);
        try {
            String[] command = new String[arguments.length + 1];
            command[0] = "/system/bin/logcat";
            System.arraycopy(arguments, 0, command, 1, arguments.length);
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
            char[] buffer = new char[4096];
            int total = 0;
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                int allowed = Math.min(count, maxChars - total);
                if (allowed > 0) {
                    output.append(buffer, 0, allowed);
                    total += allowed;
                }
                if (total >= maxChars) {
                    output.append("\n<logcat truncated>\n");
                    break;
                }
            }
        } catch (Throwable error) {
            output.append("unavailable=").append(error.getClass().getSimpleName())
                    .append('\n');
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // Keep the report already collected.
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
        return output.toString();
    }

    private static boolean selectorUnsupported(String output, String selectorName) {
        if (output == null) {
            return false;
        }
        String lower = output.toLowerCase(Locale.ROOT);
        return lower.contains("unknown option")
                && lower.contains(selectorName.toLowerCase(Locale.US));
    }

    private static boolean uidFormatUnsupported(String output) {
        if (output == null) {
            return true;
        }
        String lower = output.toLowerCase(Locale.US);
        return lower.contains("unknown format: uid")
                || lower.contains("unknown format 'uid'")
                || lower.contains("invalid format: uid")
                || lower.contains("invalid format 'uid'")
                || (lower.contains("unknown option") && lower.contains("-v uid"));
    }

    private static boolean containsPrivateReference(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.indexOf('/') >= 0
                || lower.indexOf('\\') >= 0
                || lower.contains("content:")
                || lower.contains("file:")
                || lower.contains("://")
                || lower.contains("token")
                || lower.contains("password");
    }

    private static void pruneOldReports(File directory) {
        File[] reports = directory.listFiles();
        if (reports == null || reports.length < MAX_REPORT_FILES) {
            return;
        }
        Arrays.sort(reports);
        int removeCount = reports.length - MAX_REPORT_FILES + 1;
        for (int i = 0; i < removeCount; i++) {
            if (reports[i].isFile() && isShareableReportName(reports[i].getName())) {
                reports[i].delete();
            }
        }
    }

    private static String utcTimestamp(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
