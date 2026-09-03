package com.codex.lle;

/**
 * Pure decision seam for retiring an event package after a runtime surface scan.
 *
 * <p>An Accessibility event package is historical evidence only. It may be retained
 * after an OEM transient surface has disappeared, so it can be cleared only after a
 * complete active/focused-window scan has proved that the latched surface is absent.
 */
final class RuntimeSurfaceBlockState {
    enum ActiveWindowState {
        PRESENT,
        ABSENT,
        UNKNOWN
    }

    private RuntimeSurfaceBlockState() {
    }

    static boolean shouldClearStaleLastWindowPackage(String activeBlockPackage,
            String normalizedLastWindowPackage, ActiveWindowState activeWindowState) {
        return activeBlockPackage != null
                && activeBlockPackage.equals(normalizedLastWindowPackage)
                && activeWindowState == ActiveWindowState.ABSENT;
    }
}
