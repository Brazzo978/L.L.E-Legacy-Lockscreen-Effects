package com.codex.lle;

import android.content.Context;

/** DEX-free ARM64 shell for the native Note 5 Sparkling Bubbles renderer. */
public final class SparklingBubblesArm64EffectView extends Note5NativeEffectView {
    public SparklingBubblesArm64EffectView(Context context) {
        super(context, Kind.SPARKLING_BUBBLES, false);
    }
}
