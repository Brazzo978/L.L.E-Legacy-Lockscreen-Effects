package com.codex.lle;

public final class RuntimeBlacklistPolicyTest {
    private RuntimeBlacklistPolicyTest() {
    }

    public static void main(String[] args) {
        require(RuntimeBlacklistPolicy.isCoreProtectedPackage(
                "com.android.systemui", "com.codex.lle64"), "SystemUI must be protected");
        require(RuntimeBlacklistPolicy.isCoreProtectedPackage(
                "com.codex.lle64", "com.codex.lle64"), "self must be protected");
        require(RuntimeBlacklistPolicy.isCoreProtectedPackage(
                "com.codex.lle.preview", "com.codex.lle64"), "LLE variants must be protected");
        require(RuntimeBlacklistPolicy.isProtectedPackage(
                "com.samsung.android.app.cocktailbarservice",
                "com.codex.lle64", true), "built-in block must be protected");
        require(!RuntimeBlacklistPolicy.isProtectedPackage(
                "com.example.reader", "com.codex.lle64", false),
                "ordinary applications must remain selectable");
        System.out.println("RuntimeBlacklistPolicyTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
