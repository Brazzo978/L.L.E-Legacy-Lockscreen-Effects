package com.codex.lle;

import android.content.Context;

/** DEX-free ARM64 shell for the native Note 5 Colored Droplet renderer. */
public final class ColourDropletArm64EffectView extends Note5NativeEffectView {
    public ColourDropletArm64EffectView(Context context) {
        this(context, false);
    }

    public ColourDropletArm64EffectView(Context context, boolean gyroEnabled) {
        super(context, Kind.COLOUR_DROPLET, gyroEnabled);
    }
}
