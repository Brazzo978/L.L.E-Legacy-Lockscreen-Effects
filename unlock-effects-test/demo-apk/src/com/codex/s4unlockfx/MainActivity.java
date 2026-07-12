package com.codex.s4unlockfx;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        if (getIntent() != null && getIntent().hasExtra(UnlockFxPrefs.MODE_INDEX)) {
            int modeIndex = UnlockFxPrefs.normalizeModeIndex(
                    getIntent().getIntExtra(UnlockFxPrefs.MODE_INDEX, 0));
            getSharedPreferences(UnlockFxPrefs.NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(UnlockFxPrefs.MODE_INDEX, modeIndex)
                    .putString(UnlockFxPrefs.MODE_NAME, UnlockFxPrefs.modeName(modeIndex))
                    .apply();
        }
        View content = OriginalSamsungEffectHost.tryCreate(this);
        if (content == null) {
            TextView error = new TextView(this);
            error.setText("Failed to load Samsung effect host");
            error.setTextColor(Color.WHITE);
            error.setTextSize(18f);
            error.setGravity(Gravity.CENTER);
            error.setBackgroundColor(Color.rgb(8, 12, 18));
            content = error;
        }
        setContentView(content);
    }
}
