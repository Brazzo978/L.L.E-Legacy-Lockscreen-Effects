package com.android.internal.policy.impl.keyguard.sec;

import android.content.Context;

public class LockscreenWallpaper {
    public static void disableFlipboardWallpaper(Context context) {
    }

    public static boolean isAdminWallpaper() {
        return false;
    }

    public static boolean isFlipboardWallpaper(Context context) {
        return false;
    }

    public static boolean isLiveWallpaper(Context context) {
        return false;
    }
}
