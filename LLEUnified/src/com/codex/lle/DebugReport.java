package com.codex.lle;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/** Creates a user-shareable, non-root diagnostic report without collecting images. */
final class DebugReport {
    private static final String DIRECTORY = "debug-reports";
    private static final String PREFIX = "LLE-debug-";
    private static final int MAX_LOGCAT_CHARS = 512 * 1024;
    private static final int MAX_REPORT_FILES = 4;

    private DebugReport() {
    }

    static File create(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        File directory = reportDirectory(appContext);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create debug report directory");
        }
        pruneOldReports(directory);

        File report = new File(directory,
                PREFIX + utcTimestamp("yyyyMMdd-HHmmss") + ".txt");
        StringBuilder body = new StringBuilder(64 * 1024);
        appendHeader(body, appContext);
        appendRuntimeState(body, appContext);
        appendPreferences(body, appContext);
        appendInternalFiles(body, appContext);
        appendLogcat(body);

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

    private static void appendHeader(StringBuilder body, Context context) {
        body.append("L.L.E debug report\n");
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
        body.append("build_display=").append(Build.DISPLAY).append('\n');
        body.append('\n');
    }

    private static void appendRuntimeState(StringBuilder body, Context context) {
        body.append("[runtime]\n");
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
        body.append(ChargingAccessibilityService.debugRuntimeSnapshot());
        body.append('\n');
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

    private static void appendPreferences(StringBuilder body, Context context) {
        body.append("[preferences]\n");
        SharedPreferences preferences = OverlayPrefs.get(context);
        Map<String, ?> values = new TreeMap<String, Object>(preferences.getAll());
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            body.append(key).append('=');
            body.append(isSensitivePreference(key, value)
                    ? "<redacted>" : String.valueOf(value));
            body.append('\n');
        }
        body.append('\n');
    }

    private static boolean isSensitivePreference(String key, Object value) {
        String lowerKey = key == null ? "" : key.toLowerCase(Locale.US);
        if (lowerKey.contains("uri")
                || lowerKey.contains("path")
                || lowerKey.contains("file")
                || lowerKey.contains("password")
                || lowerKey.contains("token")) {
            return true;
        }
        if (!(value instanceof String)) {
            return false;
        }
        String text = ((String) value).trim().toLowerCase(Locale.US);
        return text.startsWith("/")
                || text.startsWith("content:")
                || text.startsWith("file:")
                || text.length() > 160;
    }

    private static void appendInternalFiles(StringBuilder body, Context context) {
        body.append("[internal_files]\n");
        File[] files = context.getFilesDir().listFiles();
        if (files == null || files.length == 0) {
            body.append("none\n\n");
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            body.append(file.getName())
                    .append(" type=").append(file.isDirectory() ? "directory" : "file")
                    .append(" bytes=").append(file.isFile() ? file.length() : -1)
                    .append('\n');
        }
        body.append('\n');
    }

    private static void appendLogcat(StringBuilder body) {
        body.append("[app_uid_logcat]\n");
        Process process = null;
        BufferedReader reader = null;
        try {
            process = new ProcessBuilder(
                    "/system/bin/logcat",
                    "-d",
                    "-v", "threadtime",
                    "-t", "2000",
                    "--uid=" + android.os.Process.myUid())
                    .redirectErrorStream(true)
                    .start();
            reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
            char[] buffer = new char[4096];
            int total = 0;
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                int allowed = Math.min(count, MAX_LOGCAT_CHARS - total);
                if (allowed > 0) {
                    body.append(buffer, 0, allowed);
                    total += allowed;
                }
                if (total >= MAX_LOGCAT_CHARS) {
                    body.append("\n<logcat truncated>\n");
                    break;
                }
            }
        } catch (Throwable error) {
            body.append("unavailable=").append(error.getClass().getSimpleName())
                    .append(": ").append(error.getMessage()).append('\n');
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
