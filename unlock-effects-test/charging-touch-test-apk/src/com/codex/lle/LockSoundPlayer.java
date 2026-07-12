package com.codex.lle;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.provider.Settings;
import android.util.Log;

import java.util.Calendar;

final class LockSoundPlayer {
    private static final String TAG = "LLELockSound";
    private static final float LOCK_VOLUME = 1.0f;

    private static final int SEASONAL_SPRING = 0;
    private static final int SEASONAL_SUMMER = 1;
    private static final int SEASONAL_AUTUMN = 2;
    private static final int SEASONAL_WINTER = 3;

    private final Context context;
    private final int[] effectSounds = new int[OverlayPrefs.EFFECT_COUNT];
    private final int[] seasonalSounds = new int[4];

    private SoundPool soundPool;

    LockSoundPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    void playEffectLock(int effect) {
        if (!systemLockSoundsEnabled()) {
            return;
        }
        ensureLoaded();
        if (soundPool == null || effect < 0 || effect >= effectSounds.length) {
            return;
        }
        play(effectSounds[effect], "effect:" + effect);
    }

    void playSeasonalLock(int seasonMode) {
        if (!systemLockSoundsEnabled()) {
            return;
        }
        ensureLoaded();
        int season = resolveSeason(seasonMode);
        if (soundPool == null || season < 0 || season >= seasonalSounds.length) {
            return;
        }
        play(seasonalSounds[season], "season:" + season);
    }

    void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        for (int i = 0; i < effectSounds.length; i++) {
            effectSounds[i] = 0;
        }
        for (int i = 0; i < seasonalSounds.length; i++) {
            seasonalSounds[i] = 0;
        }
    }

    private void ensureLoaded() {
        if (soundPool != null) {
            return;
        }
        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();

        effectSounds[OverlayPrefs.EFFECT_S4_LENS_FLARE] =
                load(R.raw.lens_flare_lock);
        effectSounds[OverlayPrefs.EFFECT_S3_RIPPLE_NATIVE] =
                load(R.raw.s3_lock);
        effectSounds[OverlayPrefs.EFFECT_S5_POPPING_COLOURS] =
                load(R.raw.particle_lock);
        effectSounds[OverlayPrefs.EFFECT_WATERCOLOUR] =
                load(R.raw.ve_watercolour_lock);
        effectSounds[OverlayPrefs.EFFECT_N5_COLOUR_DROPLET] =
                load(R.raw.ve_colourdroplet_lock);
        effectSounds[OverlayPrefs.EFFECT_N5_COLOUR_DROPLET_GYRO] =
                effectSounds[OverlayPrefs.EFFECT_N5_COLOUR_DROPLET];
        effectSounds[OverlayPrefs.EFFECT_N5_SPARKLING_BUBBLES] =
                load(R.raw.ve_sparklingbubbles_lock);
        effectSounds[OverlayPrefs.EFFECT_S4_ABSTRACT_TILES] =
                load(R.raw.abstracttile_lock);
        effectSounds[OverlayPrefs.EFFECT_S4_GEOMETRIC_MOSAIC] =
                load(R.raw.geometricmosaic_lock);

        seasonalSounds[SEASONAL_SPRING] = load(R.raw.spring_lock);
        seasonalSounds[SEASONAL_SUMMER] = load(R.raw.summer_lock);
        seasonalSounds[SEASONAL_AUTUMN] = load(R.raw.autumn_lock);
        seasonalSounds[SEASONAL_WINTER] = load(R.raw.winter_lock);
    }

    private int load(int resId) {
        return soundPool.load(context, resId, 1);
    }

    private void play(int soundId, String source) {
        if (soundId == 0) {
            Log.w(TAG, "missing lock sound source=" + source);
            return;
        }
        soundPool.play(soundId, LOCK_VOLUME, LOCK_VOLUME, 0, 0, 1.0f);
        Log.i(TAG, "lock sound played source=" + source);
    }

    private boolean systemLockSoundsEnabled() {
        return Settings.System.getInt(context.getContentResolver(),
                "lockscreen_sounds_enabled", 1) != 0;
    }

    private int resolveSeason(int seasonMode) {
        if (seasonMode >= SeasonalDoodleView.SEASON_SPRING
                && seasonMode <= SeasonalDoodleView.SEASON_WINTER) {
            return seasonMode;
        }
        int month = Calendar.getInstance().get(Calendar.MONTH);
        if (month >= Calendar.MARCH && month <= Calendar.MAY) {
            return SeasonalDoodleView.SEASON_SPRING;
        }
        if (month >= Calendar.JUNE && month <= Calendar.AUGUST) {
            return SeasonalDoodleView.SEASON_SUMMER;
        }
        if (month >= Calendar.SEPTEMBER && month <= Calendar.NOVEMBER) {
            return SeasonalDoodleView.SEASON_AUTUMN;
        }
        return SeasonalDoodleView.SEASON_WINTER;
    }
}
