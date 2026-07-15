package com.codex.lle;

import android.graphics.Bitmap;

interface BackgroundSourceRenderer {
    String SHARED_CACHE_SOURCE = "cached_effect_background";

    boolean hasBackgroundSourceBitmap();

    void setBackgroundSourceBitmap(Bitmap source, String sourceName);

    void clearBackgroundSourceBitmap();

    default boolean isUsingBackgroundSourceBitmap(Bitmap bitmap) {
        return false;
    }

    static boolean canBorrowSharedCache(Bitmap source, String sourceName,
            int width, int height) {
        return SHARED_CACHE_SOURCE.equals(sourceName)
                && source != null
                && !source.isRecycled()
                && source.getConfig() == Bitmap.Config.ARGB_8888
                && source.getWidth() == Math.max(1, width)
                && source.getHeight() == Math.max(1, height);
    }
}
