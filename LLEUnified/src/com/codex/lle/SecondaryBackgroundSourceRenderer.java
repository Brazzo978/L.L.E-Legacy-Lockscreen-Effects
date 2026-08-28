package com.codex.lle;

import android.graphics.Bitmap;

/** Optional second full-screen source used independently from the primary lockscreen cache. */
interface SecondaryBackgroundSourceRenderer {
    boolean hasSecondaryBackgroundSourceBitmap();

    void setSecondaryBackgroundSourceBitmap(Bitmap source, String sourceName);

    void clearSecondaryBackgroundSourceBitmap();
}
