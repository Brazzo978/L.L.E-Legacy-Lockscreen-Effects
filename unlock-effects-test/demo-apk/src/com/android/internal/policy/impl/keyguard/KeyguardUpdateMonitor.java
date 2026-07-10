package com.android.internal.policy.impl.keyguard;

import android.content.Context;

public class KeyguardUpdateMonitor {
    private static final KeyguardUpdateMonitor INSTANCE = new KeyguardUpdateMonitor();

    public static KeyguardUpdateMonitor getInstance(Context context) {
        return INSTANCE;
    }

    public boolean hasBootCompleted() {
        return true;
    }
}
