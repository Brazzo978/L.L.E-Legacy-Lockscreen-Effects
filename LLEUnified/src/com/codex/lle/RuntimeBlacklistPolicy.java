package com.codex.lle;

import java.util.Locale;

/** Pure policy shared by the app picker and host tests. */
final class RuntimeBlacklistPolicy {
    private RuntimeBlacklistPolicy() {
    }

    static boolean isCoreProtectedPackage(String packageName, String selfPackage) {
        String value = normalize(packageName);
        String self = normalize(selfPackage);
        return "android".equals(value)
                || "com.android.systemui".equals(value)
                || "com.samsung.android.app.aodservice".equals(value)
                || (!self.isEmpty() && self.equals(value))
                || value.startsWith("com.codex.lle");
    }

    static boolean isProtectedPackage(String packageName, String selfPackage,
            boolean builtInRuntimeBlock) {
        return builtInRuntimeBlock || isCoreProtectedPackage(packageName, selfPackage);
    }

    private static String normalize(String packageName) {
        return packageName == null
                ? "" : packageName.trim().toLowerCase(Locale.US);
    }
}
