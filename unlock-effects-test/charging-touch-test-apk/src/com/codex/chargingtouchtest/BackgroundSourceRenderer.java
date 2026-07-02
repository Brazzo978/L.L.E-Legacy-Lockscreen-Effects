package com.codex.chargingtouchtest;

import android.graphics.Bitmap;

interface BackgroundSourceRenderer {
    boolean hasBackgroundSourceBitmap();

    void setBackgroundSourceBitmap(Bitmap source, String sourceName);

    void clearBackgroundSourceBitmap();
}
