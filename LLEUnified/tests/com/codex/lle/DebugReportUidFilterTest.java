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

        System.out.println("DebugReportUidFilterTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
