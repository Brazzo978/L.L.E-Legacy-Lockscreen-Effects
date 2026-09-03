package com.codex.lle;

/** Host regression for stale OEM runtime-surface event packages. */
public final class RuntimeSurfaceBlockStateTest {
    private static final String COCKTAIL_BAR =
            "com.samsung.android.app.cocktailbarservice";

    private RuntimeSurfaceBlockStateTest() {
    }

    public static void main(String[] args) {
        testStaleCocktailBarPackageClearsAfterConclusiveAbsence();
        testFocusedCocktailBarWindowKeepsBlock();
        testUnknownWindowStateKeepsBlock();
        testDifferentLastPackageIsNeverCleared();
        System.out.println("RuntimeSurfaceBlockStateTest: PASS");
    }

    private static void testStaleCocktailBarPackageClearsAfterConclusiveAbsence() {
        require(RuntimeSurfaceBlockState.shouldClearStaleLastWindowPackage(
                        COCKTAIL_BAR,
                        COCKTAIL_BAR,
                        RuntimeSurfaceBlockState.ActiveWindowState.ABSENT),
                "stale Cocktail Bar event package should clear after conclusive absence");
    }

    private static void testFocusedCocktailBarWindowKeepsBlock() {
        require(!RuntimeSurfaceBlockState.shouldClearStaleLastWindowPackage(
                        COCKTAIL_BAR,
                        COCKTAIL_BAR,
                        RuntimeSurfaceBlockState.ActiveWindowState.PRESENT),
                "active or focused Cocktail Bar window must keep block");
    }

    private static void testUnknownWindowStateKeepsBlock() {
        require(!RuntimeSurfaceBlockState.shouldClearStaleLastWindowPackage(
                        COCKTAIL_BAR,
                        COCKTAIL_BAR,
                        RuntimeSurfaceBlockState.ActiveWindowState.UNKNOWN),
                "inconclusive window scan must keep block");
    }

    private static void testDifferentLastPackageIsNeverCleared() {
        require(!RuntimeSurfaceBlockState.shouldClearStaleLastWindowPackage(
                        COCKTAIL_BAR,
                        "com.android.systemui",
                        RuntimeSurfaceBlockState.ActiveWindowState.ABSENT),
                "a newer event package must not be cleared");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
