package com.codex.lle;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class EffectBackgroundWakeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        String profile = FoldDisplayTarget.cacheProfileForContext(this);
        if (!OverlayPrefs.unlockEffectEnabled(this)
                || !OverlayPrefs.foldPanelUnlockEffectEnabled(this, profile)) {
            finish();
            return;
        }
        int effect = getIntent().getIntExtra("effect", -1);
        if (effect >= 0) {
            OverlayPrefs.get(this).edit()
                    .putInt(OverlayPrefs.UNLOCK_EFFECT, effect)
                    .apply();
        }
        OverlayPrefs.get(this).edit()
                .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_ACTIVE, true)
                .putBoolean(OverlayPrefs.EFFECT_BACKGROUND_WAKE_CAPTURE_SHOULD_RELOCK, true)
                .apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0f);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
                overridePendingTransition(0, 0);
            }
        }, 240L);
    }
}
