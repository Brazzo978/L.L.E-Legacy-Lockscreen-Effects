package com.android.internal.policy.impl.keyguard;

import android.content.Context;

public final class KeyguardUpdateMonitor {
    private static final KeyguardUpdateMonitor INSTANCE = new KeyguardUpdateMonitor();

    private KeyguardUpdateMonitor() {
    }

    public static KeyguardUpdateMonitor getInstance(Context context) {
        return INSTANCE;
    }

    public boolean hasBootCompleted() {
        return true;
    }
}
