package com.codex.lle;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/** Writes debug-only effect layers without adding work to the normal render path. */
final class DebugFrameCaptureFiles {
    private DebugFrameCaptureFiles() {
    }

    static void saveAsync(final Context context, final Bitmap frame,
            final String fileName, final String logTag, final long phaseMs) {
        if (frame == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                File directory = context.getExternalFilesDir("debug-captures");
                File output = directory == null ? null : new File(directory, fileName);
                try {
                    if (directory == null || (!directory.isDirectory()
                            && !directory.mkdirs())) {
                        throw new IllegalStateException("debug capture directory unavailable");
                    }
                    FileOutputStream stream = new FileOutputStream(output);
                    try {
                        if (!frame.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            throw new IllegalStateException("PNG compression failed");
                        }
                    } finally {
                        stream.close();
                    }
                    Log.i(logTag, "debug hint capture saved phaseMs=" + phaseMs
                            + " path=" + output.getAbsolutePath()
                            + " bytes=" + output.length());
                } catch (Throwable t) {
                    Log.e(logTag, "debug hint capture failed phaseMs=" + phaseMs, t);
                } finally {
                    frame.recycle();
                }
            }
        }, "LLE-hint-capture-writer").start();
    }
}
