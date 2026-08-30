package com.codex.lle;

import java.io.File;

/** Optional direct-file background path for GLES renderers that do not need a Java Bitmap. */
interface RawArgb8888BackgroundRenderer {
    /** True once a valid raw source has been accepted for the current renderer lifecycle. */
    boolean hasRawArgb8888BackgroundSource();

    /**
     * Queues a private, CRC-checked ARGB8888 file for upload on the renderer's GL thread.
     * Implementations must not retain a caller-owned file descriptor or mapped buffer.
     */
    void setRawArgb8888BackgroundSource(File file, String sourceName);
}
