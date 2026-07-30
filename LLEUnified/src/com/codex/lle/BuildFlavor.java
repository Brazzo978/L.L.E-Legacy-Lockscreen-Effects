package com.codex.lle;

/**
 * ARM32 fallback flavor.
 *
 * <p>The ARM64 build excludes this source and generates the authoritative
 * flavor class in its output directory. Keeping the shared-source fallback
 * legacy-enabled preserves the frozen ARM32 product unchanged.</p>
 */
final class BuildFlavor {
    static final boolean LEGACY_VENDOR_EFFECTS = true;
    static final String NAME = "arm32-legacy";

    private BuildFlavor() {
    }
}
