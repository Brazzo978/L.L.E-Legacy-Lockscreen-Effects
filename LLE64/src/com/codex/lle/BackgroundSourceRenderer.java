package com.codex.lle;

import android.graphics.Bitmap;

interface BackgroundSourceRenderer {
    boolean hasBackgroundSourceBitmap();

    void setBackgroundSourceBitmap(Bitmap source, String sourceName);

    void clearBackgroundSourceBitmap();
}
