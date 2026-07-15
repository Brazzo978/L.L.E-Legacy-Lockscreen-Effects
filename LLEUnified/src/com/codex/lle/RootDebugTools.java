package com.codex.lle;

import android.content.Context;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

final class RootDebugTools {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_TEXT_BYTES = 256 * 1024;
    private static final int MAX_REPORT_COMMAND_BYTES = 128 * 1024;
    private static final long DEFAULT_TIMEOUT_MS = 12000L;

    private RootDebugTools() {
    }

    static Result checkRoot() {
        CommandResult result = runSuCommand("id", 8192, DEFAULT_TIMEOUT_MS);
        String out = text(result.stdout).trim();
        String err = text(result.stderr).trim();
        if (result.succeeded() && out.contains("uid=0")) {
            return Result.ok("Root OK: " + out, null);
        }
        return Result.error("Root unavailable: " + commandSummary(result, out, err), null);
    }

    static Result captureTouchEvents(Context context, int durationMs) {
        int seconds = Math.max(1, Math.min(10, (durationMs + 999) / 1000));
        String command = "getevent -lt & pid=$!; sleep " + seconds
                + "; kill $pid 2>/dev/null; wait $pid 2>/dev/null";
        long startedUptimeMs = SystemClock.uptimeMillis();
        CommandResult result = runSuCommand(command, MAX_TEXT_BYTES, seconds * 1000L + 8000L);
        String out = text(result.stdout);
        String err = text(result.stderr);
        if (result.timedOut) {
            return Result.error("Root touch capture timed out: " + err.trim(), null);
        }
        if (out.trim().isEmpty() && !err.trim().isEmpty()) {
            return Result.error("Root touch capture failed: " + err.trim(), null);
        }

        File file = new File(context.getFilesDir(), "root_touch_events.txt");
        TouchCaptureStats stats = analyzeTouchCapture(out, startedUptimeMs);
        StringBuilder body = new StringBuilder();
        body.append("LLE root touch capture\n");
        body.append("duration_seconds=").append(seconds).append('\n');
        body.append("exit_code=").append(result.exitCode).append('\n');
        body.append("event_lines=").append(stats.eventLines).append('\n');
        body.append("touch_lines=").append(stats.touchLines).append('\n');
        body.append("position_lines=").append(stats.positionLines).append('\n');
        body.append("syn_reports=").append(stats.synReports).append('\n');
        body.append("first_touch_delay_ms=").append(stats.firstTouchDelayMs).append('\n');
        body.append("touch_device=").append(stats.touchDevice == null ? "-" : stats.touchDevice).append('\n');
        if (result.stdoutTruncated) {
            body.append("stdout_truncated=true\n");
        }
        body.append('\n');
        if (out.trim().isEmpty()) {
            body.append("No touch events captured.\n");
        } else {
            body.append(out);
            if (!out.endsWith("\n")) {
                body.append('\n');
            }
        }
        if (!err.trim().isEmpty()) {
            body.append("\n[stderr]\n").append(err);
            if (!err.endsWith("\n")) {
                body.append('\n');
            }
        }
        try {
            writeText(file, body.toString());
        } catch (IOException e) {
            return Result.error("Root touch capture save failed: " + e.getMessage(), file);
        }
        return Result.ok("Root touch capture saved: " + file.getAbsolutePath()
                + " touchLines=" + stats.touchLines
                + " posLines=" + stats.positionLines
                + " firstDelayMs=" + stats.firstTouchDelayMs, file);
    }

    static Result writeDebugReport(Context context) {
        String packageName = context.getPackageName();
        File file = new File(context.getFilesDir(), "root_debug_report.txt");
        StringBuilder report = new StringBuilder();
        report.append("LLE root debug report\n");
        report.append("package=").append(packageName).append("\n");
        report.append("time_ms=").append(System.currentTimeMillis()).append("\n\n");

        appendCommand(report, "id", 3000L);
        appendCommand(report, "getprop ro.build.version.release", 3000L);
        appendCommand(report, "getprop ro.product.manufacturer", 3000L);
        appendCommand(report, "getprop ro.product.model", 3000L);
        appendCommand(report, "settings get secure enabled_accessibility_services", 3000L);
        appendCommand(report, "dumpsys activity services " + packageName, 4000L);
        appendCommand(report, "dumpsys window policy", 4000L);
        appendCommand(report, "dumpsys power", 4000L);
        appendCommand(report, "dumpsys input", 4000L);
        appendCommand(report, "dumpsys deviceidle", 4000L);
        appendCommand(report, "cmd appops get " + packageName, 4000L);
        appendCommand(report, "dumpsys gfxinfo " + packageName + " framestats", 5000L);

        try {
            writeText(file, report.toString());
        } catch (IOException e) {
            return Result.error("Root report save failed: " + e.getMessage(), file);
        }
        return Result.ok("Root debug report saved: " + file.getAbsolutePath(), file);
    }

    static Result writeKeepAlivePlan(Context context) {
        String packageName = context.getPackageName();
        File file = new File(context.getFilesDir(), "root_keepalive_plan.txt");
        StringBuilder body = new StringBuilder();
        body.append("LLE optional root keepalive plan\n\n");
        body.append("These commands are intentionally not auto-applied by the app.\n");
        body.append("Apply only during an explicit root/ADB test, then revert if needed.\n\n");
        body.append("Enable:\n");
        body.append("su -c 'dumpsys deviceidle whitelist +").append(packageName).append("'\n");
        body.append("su -c 'cmd appops set ").append(packageName)
                .append(" RUN_ANY_IN_BACKGROUND allow'\n");
        body.append("su -c 'cmd appops set ").append(packageName)
                .append(" RUN_IN_BACKGROUND allow'\n");
        body.append("su -c 'cmd appops set ").append(packageName)
                .append(" WAKE_LOCK allow'\n");
        body.append("su -c 'am set-inactive ").append(packageName).append(" false'\n\n");
        body.append("Revert:\n");
        body.append("su -c 'dumpsys deviceidle whitelist -").append(packageName).append("'\n");
        body.append("su -c 'cmd appops set ").append(packageName)
                .append(" RUN_ANY_IN_BACKGROUND default'\n");
        body.append("su -c 'cmd appops set ").append(packageName)
                .append(" RUN_IN_BACKGROUND default'\n");
        body.append("su -c 'cmd appops set ").append(packageName)
                .append(" WAKE_LOCK default'\n");
        try {
            writeText(file, body.toString());
        } catch (IOException e) {
            return Result.error("Root keepalive plan save failed: " + e.getMessage(), file);
        }
        return Result.ok("Root keepalive plan saved: " + file.getAbsolutePath(), file);
    }

    static Result applyKeepAlivePlan(Context context) {
        String packageName = context.getPackageName();
        String command = "dumpsys deviceidle whitelist +" + packageName
                + "; cmd appops set " + packageName + " RUN_ANY_IN_BACKGROUND allow"
                + "; cmd appops set " + packageName + " RUN_IN_BACKGROUND allow"
                + "; cmd appops set " + packageName + " WAKE_LOCK allow"
                + "; am set-inactive " + packageName + " false";
        CommandResult result = runSuCommand(command, MAX_TEXT_BYTES, DEFAULT_TIMEOUT_MS);
        String out = text(result.stdout).trim();
        String err = text(result.stderr).trim();
        if (result.succeeded()) {
            return Result.ok("Root keepalive applied"
                    + (out.isEmpty() ? "" : ": " + out), null);
        }
        return Result.error("Root keepalive failed: "
                + commandSummary(result, out, err), null);
    }

    static Result revertKeepAlivePlan(Context context) {
        String packageName = context.getPackageName();
        String command = "dumpsys deviceidle whitelist -" + packageName
                + "; cmd appops set " + packageName + " RUN_ANY_IN_BACKGROUND default"
                + "; cmd appops set " + packageName + " RUN_IN_BACKGROUND default"
                + "; cmd appops set " + packageName + " WAKE_LOCK default"
                + "; am set-inactive " + packageName + " false";
        CommandResult result = runSuCommand(command, MAX_TEXT_BYTES, DEFAULT_TIMEOUT_MS);
        String out = text(result.stdout).trim();
        String err = text(result.stderr).trim();
        if (result.succeeded()) {
            return Result.ok("Root keepalive reverted"
                    + (out.isEmpty() ? "" : ": " + out), null);
        }
        return Result.error("Root keepalive revert failed: "
                + commandSummary(result, out, err), null);
    }

    private static void appendCommand(StringBuilder report, String command, long timeoutMs) {
        report.append("$ su -c \"").append(command).append("\"\n");
        CommandResult result = runSuCommand(command, MAX_REPORT_COMMAND_BYTES, timeoutMs);
        report.append("exit_code=").append(result.exitCode)
                .append(" timed_out=").append(result.timedOut).append('\n');
        if (result.stdoutTruncated) {
            report.append("stdout_truncated=true\n");
        }
        String out = text(result.stdout);
        String err = text(result.stderr);
        if (!out.isEmpty()) {
            report.append(out);
            if (!out.endsWith("\n")) {
                report.append('\n');
            }
        }
        if (!err.trim().isEmpty()) {
            report.append("[stderr]\n").append(err);
            if (!err.endsWith("\n")) {
                report.append('\n');
            }
        }
        report.append('\n');
    }

    private static TouchCaptureStats analyzeTouchCapture(String output, long startedUptimeMs) {
        TouchCaptureStats stats = new TouchCaptureStats();
        if (output == null || output.isEmpty()) {
            return stats;
        }
        String[] lines = output.split("\\r?\\n");
        String pendingDevice = null;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            int deviceIndex = line.indexOf("/dev/input/event");
            if (line.startsWith("add device") && deviceIndex >= 0) {
                pendingDevice = readDevicePath(line, deviceIndex);
                continue;
            }
            String lower = line.toLowerCase();
            if (pendingDevice != null
                    && lower.indexOf("name:") >= 0
                    && lower.indexOf("touchscreen") >= 0) {
                stats.touchDevice = pendingDevice;
                break;
            }
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            boolean eventLine = stats.touchDevice == null
                    ? line.indexOf("/dev/input/event") >= 0
                    : line.indexOf(stats.touchDevice) >= 0;
            if (!eventLine) {
                continue;
            }
            stats.eventLines++;
            String upper = line.toUpperCase();
            boolean touchLine = upper.indexOf("ABS_MT") >= 0
                    || upper.indexOf("BTN_TOUCH") >= 0
                    || upper.indexOf("ABS_X") >= 0
                    || upper.indexOf("ABS_Y") >= 0
                    || upper.indexOf("SYN_REPORT") >= 0;
            if (touchLine) {
                stats.touchLines++;
                if (stats.firstTouchDelayMs < 0L) {
                    long eventTimeMs = parseGeteventTimeMs(line);
                    if (eventTimeMs >= 0L) {
                        stats.firstTouchDelayMs = eventTimeMs - startedUptimeMs;
                    }
                }
            }
            if (upper.indexOf("ABS_MT_POSITION") >= 0
                    || upper.indexOf("ABS_X") >= 0
                    || upper.indexOf("ABS_Y") >= 0) {
                stats.positionLines++;
            }
            if (upper.indexOf("SYN_REPORT") >= 0) {
                stats.synReports++;
            }
        }
        return stats;
    }

    private static String readDevicePath(String line, int deviceIndex) {
        int end = line.indexOf(' ', deviceIndex);
        if (end < 0) {
            end = line.length();
        }
        return line.substring(deviceIndex, end).trim();
    }

    private static long parseGeteventTimeMs(String line) {
        int start = line.indexOf('[');
        int end = line.indexOf(']', start + 1);
        if (start < 0 || end <= start) {
            return -1L;
        }
        try {
            double seconds = Double.parseDouble(line.substring(start + 1, end).trim());
            return Math.round(seconds * 1000.0);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static CommandResult runSuCommand(String command, int maxStdoutBytes, long timeoutMs) {
        Process process = null;
        StreamCollector stdout = null;
        StreamCollector stderr = null;
        try {
            process = new ProcessBuilder("su", "-c", command).start();
            stdout = new StreamCollector(process.getInputStream(), maxStdoutBytes);
            stderr = new StreamCollector(process.getErrorStream(), 64 * 1024);
            stdout.start();
            stderr.start();
            int exitCode = waitFor(process, timeoutMs);
            boolean timedOut = exitCode == Integer.MIN_VALUE;
            if (timedOut) {
                process.destroy();
                SystemClock.sleep(150L);
                exitCode = safeExitValue(process, -1);
            }
            joinQuietly(stdout, 500L);
            joinQuietly(stderr, 500L);
            return new CommandResult(exitCode, timedOut,
                    stdout.snapshot(), stderr.snapshot(),
                    stdout.truncated(), stderr.truncated());
        } catch (IOException e) {
            return new CommandResult(-1, false, new byte[0],
                    e.getMessage().getBytes(UTF_8), false, false);
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private static final class TouchCaptureStats {
        int eventLines;
        int touchLines;
        int positionLines;
        int synReports;
        long firstTouchDelayMs = -1L;
        String touchDevice;
    }

    private static int waitFor(Process process, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                return process.exitValue();
            } catch (IllegalThreadStateException ignored) {
                SystemClock.sleep(25L);
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int safeExitValue(Process process, int fallback) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException e) {
            return fallback;
        }
    }

    private static String commandSummary(CommandResult result, String out, String err) {
        StringBuilder summary = new StringBuilder();
        summary.append("exit=").append(result.exitCode);
        if (result.timedOut) {
            summary.append(" timeout");
        }
        if (!out.trim().isEmpty()) {
            summary.append(" stdout=").append(singleLine(out));
        }
        if (!err.trim().isEmpty()) {
            summary.append(" stderr=").append(singleLine(err));
        }
        return summary.toString();
    }

    private static String singleLine(String text) {
        String clean = text.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 160 ? clean.substring(0, 160) + "..." : clean;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, UTF_8);
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
    }

    private static void writeText(File file, String text) throws IOException {
        writeBytes(file, text.getBytes(UTF_8));
    }

    private static void joinQuietly(Thread thread, long timeoutMs) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    static final class Result {
        final boolean success;
        final String message;
        final File file;

        private Result(boolean success, String message, File file) {
            this.success = success;
            this.message = message;
            this.file = file;
        }

        static Result ok(String message, File file) {
            return new Result(true, message, file);
        }

        static Result error(String message, File file) {
            return new Result(false, message, file);
        }
    }

    private static final class CommandResult {
        final int exitCode;
        final boolean timedOut;
        final byte[] stdout;
        final byte[] stderr;
        final boolean stdoutTruncated;
        final boolean stderrTruncated;

        CommandResult(int exitCode, boolean timedOut, byte[] stdout, byte[] stderr,
                boolean stdoutTruncated, boolean stderrTruncated) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.stdout = stdout;
            this.stderr = stderr;
            this.stdoutTruncated = stdoutTruncated;
            this.stderrTruncated = stderrTruncated;
        }

        boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }

    private static final class StreamCollector extends Thread {
        private final InputStream stream;
        private final int maxBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        StreamCollector(InputStream stream, int maxBytes) {
            this.stream = stream;
            this.maxBytes = maxBytes;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            try {
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    synchronized (this) {
                        int allowed = maxBytes - buffer.size();
                        if (allowed > 0) {
                            buffer.write(chunk, 0, Math.min(read, allowed));
                        }
                        if (read > allowed) {
                            truncated = true;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        synchronized byte[] snapshot() {
            return buffer.toByteArray();
        }

        synchronized boolean truncated() {
            return truncated;
        }
    }
}
