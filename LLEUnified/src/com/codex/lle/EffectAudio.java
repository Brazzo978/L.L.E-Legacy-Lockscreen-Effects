package com.codex.lle;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.provider.Settings;

/** Central audio policy shared by every L.L.E. SoundPool. */
final class EffectAudio {
    private static final String LOCKSCREEN_SOUNDS_SETTING = "lockscreen_sounds_enabled";

    private EffectAudio() {
    }

    static AudioAttributes soundPoolAttributes(Context context) {
        boolean mediaRoute = routesThroughMedia(context);
        return new AudioAttributes.Builder()
                .setUsage(mediaRoute
                        ? AudioAttributes.USAGE_MEDIA
                        : AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(mediaRoute
                        ? AudioAttributes.CONTENT_TYPE_MUSIC
                        : AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    static int streamType(Context context) {
        return routesThroughMedia(context)
                ? AudioManager.STREAM_MUSIC
                : AudioManager.STREAM_SYSTEM;
    }

    static boolean routesThroughMedia(Context context) {
        return context != null && OverlayPrefs.useMediaAudioRoute(context);
    }

    static String routeLabel(Context context) {
        return routesThroughMedia(context) ? "media" : "system";
    }

    static boolean platformSoundSwitchAllows(Context context) {
        if (routesThroughMedia(context)) {
            return true;
        }
        try {
            return Settings.System.getInt(context.getContentResolver(),
                    LOCKSCREEN_SOUNDS_SETTING, 1) != 0;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    static boolean outputHasVolume(Context context, AudioManager audioManager) {
        if (audioManager == null) {
            return true;
        }
        try {
            int stream = streamType(context);
            return audioManager.getStreamVolume(stream) > 0
                    && !audioManager.isStreamMute(stream);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    static boolean ringerModeAllows(Context context, AudioManager audioManager) {
        if (routesThroughMedia(context) || audioManager == null) {
            return true;
        }
        try {
            int mode = audioManager.getRingerMode();
            return mode != AudioManager.RINGER_MODE_SILENT
                    && mode != AudioManager.RINGER_MODE_VIBRATE;
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}