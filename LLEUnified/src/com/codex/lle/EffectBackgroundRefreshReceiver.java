package com.codex.lle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class EffectBackgroundRefreshReceiver extends BroadcastReceiver {
    private static final String TAG = "ChargingA11y";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!OverlayPrefs.masterEnabled(context)
                || !OverlayPrefs.effectBackgroundAutoRefreshEnabled(context)
                || !OverlayPrefs.effectBackgroundForceRecapture(context)) {
            return;
        }
        String profile = FoldDisplayTarget.cacheProfileForContext(context);
        if (!OverlayPrefs.unlockEffectEnabled(context)
                || !OverlayPrefs.foldPanelUnlockEffectEnabled(context, profile)) {
            Log.i(TAG, "effect background refresh skipped for disabled panel=" + profile);
            return;
        }
        OverlayPrefs.get(context).edit()
                .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, true)
                .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK, true)
                .apply();
        Intent wake = new Intent(context, EffectBackgroundWakeActivity.class);
        wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            context.startActivity(wake);
            Log.i(TAG, "effect background refresh wake requested");
        } catch (Throwable t) {
            OverlayPrefs.get(context).edit()
                    .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, false)
                    .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK, false)
                    .apply();
            Log.d(TAG, "effect background refresh wake failed", t);
        }
    }
}
