package com.codex.lle;

/** Host regression for the Android 11 UID-formatted logcat compatibility path. */
public final class DebugReportUidFilterTest {
    private DebugReportUidFilterTest() {
    }

    public static void main(String[] args) {
        String raw =
                "--------- beginning of main\n"
                + "08-12 12:00:00.001 10234 111 112 E App: numeric old pid\n"
                + "08-12 12:00:00.002 u0_a234 211 212 I App: alias current pid\n"
                + "08-12 12:00:00.003 u0a234 311 312 W App: compact alias\n"
                + "08-12 12:00:00.004 1000 411 412 E System: unrelated\n"
                + "08-12 12:00:00.005 shell 511 512 D Shell: unrelated\n";

        String filtered = DebugReport.filterUidFormattedLogcat(raw, 10234);
        require(filtered.startsWith("uid_format_matching_lines=3\n"), "match count");
        require(filtered.contains("numeric old pid"), "numeric UID");
        require(filtered.contains("alias current pid"), "Android app alias");
        require(filtered.contains("compact alias"), "compact app alias");
        require(!filtered.contains("System: unrelated"), "system UID leaked");
        require(!filtered.contains("Shell: unrelated"), "shell UID leaked");

        String secondaryUser =
                "08-12 12:00:01.001 u1_a234 611 612 E App: secondary user\n";
        String secondaryFiltered = DebugReport.filterUidFormattedLogcat(
                secondaryUser, 110234);
        require(secondaryFiltered.contains("secondary user"), "secondary-user alias");

        String coordinateLog = "touch=120,640 point=-3.5,99.0 x=44 y=55 "
                + "center=1,2 from=3,4 to=5,6 box=7,8,9,10 state=ready";
        String redacted = DebugReport.redactLogcatCoordinates(coordinateLog);
        require(!redacted.contains("120,640"), "touch coordinates leaked");
        require(!redacted.contains("-3.5,99.0"), "point coordinates leaked");
        require(!redacted.contains("x=44 y=55"), "x/y coordinates leaked");
        require(!redacted.contains("center=1,2"), "center coordinates leaked");
        require(!redacted.contains("from=3,4"), "from coordinates leaked");
        require(!redacted.contains("to=5,6"), "to coordinates leaked");
        require(!redacted.contains("box=7,8,9,10"), "box coordinates leaked");
        require(redacted.contains("touch=<redacted>"), "touch marker missing");
        require(redacted.contains("state=ready"), "safe diagnostics removed");

        StringBuilder runtime = new StringBuilder();
        DebugReport.appendSanitizedRuntimeSnapshot(runtime,
                "notification_shade_diagnostic_reason="
                        + "event:window_content:shade_probe\n"
                        + "background_delivery_path=raw_direct\n"
                        + "colour_view_background_ownership=shared_cache_borrow\n"
                        + "active_display_current_refresh_millihz=120000\n");
        require(runtime.toString().contains(
                "notification_shade_diagnostic_reason=event:window_content:shade_probe"),
                "internal content reason omitted");
        require(runtime.toString().contains("background_delivery_path=raw_direct"),
                "background delivery path omitted");
        require(runtime.toString().contains(
                "colour_view_background_ownership=shared_cache_borrow"),
                "bitmap ownership omitted");
        require(runtime.toString().contains("runtime_snapshot_schema_version=2"),
                "runtime schema missing");
        require(runtime.toString().contains("runtime_snapshot_omitted_fields=0"),
                "known runtime field unexpectedly omitted");

        StringBuilder privateRuntime = new StringBuilder();
        DebugReport.appendSanitizedRuntimeSnapshot(privateRuntime,
                "effect_readiness_detail=file:C:/private/path\n");
        require(privateRuntime.toString().contains("runtime_snapshot_omitted_fields=1"),
                "private runtime reference was not rejected");

        StringBuilder advancedRuntime = new StringBuilder();
        DebugReport.appendRuntimeSnapshot(advancedRuntime,
                "last_window_package=com.example.private\n"
                        + "effect_readiness_detail=file:C:/private/path\n"
                        + "touch_resolved_dimensions=1080x2340\n",
                true);
        require(advancedRuntime.toString().contains("runtime_snapshot_filter=none"),
                "advanced runtime filter marker missing");
        require(advancedRuntime.toString().contains(
                "last_window_package=com.example.private"),
                "advanced package value was filtered");
        require(advancedRuntime.toString().contains(
                "effect_readiness_detail=file:C:/private/path"),
                "advanced path value was filtered");
        require(advancedRuntime.toString().contains("runtime_snapshot_omitted_fields=0"),
                "advanced runtime unexpectedly omitted data");
        require(DebugReport.isAdvancedReportName(
                        "LLE-debug-advanced-20260814-120000.txt"),
                "advanced report filename rejected");
        require(DebugReport.isShareableReportName(
                        "LLE-debug-advanced-20260814-120000.txt"),
                "advanced report filename not shareable");

        System.out.println("DebugReportUidFilterTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
