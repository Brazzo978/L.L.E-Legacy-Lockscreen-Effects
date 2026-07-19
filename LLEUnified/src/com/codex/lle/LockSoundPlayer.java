package com.codex.lle;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.provider.Settings;
import android.util.Log;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private final Object soundLock = new Object();
    private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
    private final Map<Integer, String> pendingSoundSources =
            new HashMap<Integer, String>();

    private SoundPool soundPool;

    LockSoundPlayer(Context context) {
        this.context = context.getApplicationContext();
        ensureLoaded();
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
        synchronized (soundLock) {
            if (soundPool != null) {
                soundPool.setOnLoadCompleteListener(null);
                soundPool.release();
                soundPool = null;
            }
            loadedSoundIds.clear();
            pendingSoundSources.clear();
            for (int i = 0; i < effectSounds.length; i++) {
                effectSounds[i] = 0;
            }
            for (int i = 0; i < seasonalSounds.length; i++) {
                seasonalSounds[i] = 0;
            }
        }
    }

    private void ensureLoaded() {
        synchronized (soundLock) {
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
            soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(
                        SoundPool completedPool, int sampleId, int status) {
                    handleLoadComplete(completedPool, sampleId, status);
                }
            });

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
    }

    private int load(int resId) {
        return soundPool.load(context, resId, 1);
    }

    private void play(int soundId, String source) {
        if (soundId == 0) {
            Log.w(TAG, "missing lock sound source=" + source);
            return;
        }
        synchronized (soundLock) {
            if (soundPool == null) {
                Log.w(TAG, "lock sound pool unavailable source=" + source);
                return;
            }
            if (!loadedSoundIds.contains(soundId)) {
                pendingSoundSources.put(soundId, source);
                Log.d(TAG, "lock sound queued until load source=" + source);
                return;
            }
            playLoadedLocked(soundId, source);
        }
    }

    private void handleLoadComplete(
            SoundPool completedPool, int sampleId, int status) {
        synchronized (soundLock) {
            if (completedPool != soundPool) {
                return;
            }
            if (status != 0) {
                String source = pendingSoundSources.remove(sampleId);
                Log.w(TAG, "lock sound load failed id=" + sampleId
                        + " status=" + status + " source=" + source);
                return;
            }
            loadedSoundIds.add(sampleId);
            String pendingSource = pendingSoundSources.remove(sampleId);
            if (pendingSource != null && systemLockSoundsEnabled()) {
                playLoadedLocked(sampleId, pendingSource + ":deferred");
            }
        }
    }

    private void playLoadedLocked(int soundId, String source) {
        int streamId = soundPool.play(
                soundId, LOCK_VOLUME, LOCK_VOLUME, 0, 0, 1.0f);
        if (streamId == 0) {
            Log.w(TAG, "lock sound play rejected source=" + source);
        } else {
            Log.i(TAG, "lock sound played source=" + source
                    + " stream=" + streamId);
        }
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
