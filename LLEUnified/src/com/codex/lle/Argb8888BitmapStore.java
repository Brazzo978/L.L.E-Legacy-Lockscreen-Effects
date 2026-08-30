package com.codex.lle;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;

/** Versioned, corruption-checked raw storage for display-sized ARGB_8888 colormaps. */
final class Argb8888BitmapStore {
    private static final int MAGIC = 0x4C4C4538; // "LLE8"
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 32;
    private static final int MAX_DIMENSION = 16384;
    private static final int CRC_CHUNK_BYTES = 64 * 1024;

    static final class Info {
        final boolean raw;
        final int width;
        final int height;
        final long payloadBytes;

        Info(boolean raw, int width, int height, long payloadBytes) {
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.payloadBytes = payloadBytes;
        }
    }

    /**
     * Read-only mapped pixels for renderers that can upload the raw colormap directly.
     *
     * <p>The payload is the native in-memory byte layout produced by
     * {@link Bitmap#copyPixelsToBuffer(java.nio.Buffer)}. On Android's little-endian ARM64
     * targets ARGB_8888 therefore reaches GLES as BGRA bytes; a direct-upload shader must
     * swizzle {@code .bgra}. The mapping remains valid until {@link #close()}.</p>
     */
    static final class MappedImage implements AutoCloseable {
        final int width;
        final int height;
        final int rowBytes;
        private final RandomAccessFile input;
        private final FileChannel channel;
        private final ByteBuffer pixels;

        MappedImage(int width, int height, int rowBytes,
                RandomAccessFile input, FileChannel channel, ByteBuffer pixels) {
            this.width = width;
            this.height = height;
            this.rowBytes = rowBytes;
            this.input = input;
            this.channel = channel;
            this.pixels = pixels;
        }

        ByteBuffer pixels() {
            ByteBuffer duplicate = pixels.duplicate();
            duplicate.position(0);
            return duplicate;
        }

        @Override
        public void close() {
            try {
                channel.close();
            } catch (Throwable ignored) {
            }
            try {
                input.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class Header {
        final int width;
        final int height;
        final int rowBytes;
        final int payloadBytes;
        final int crc32;

        Header(int width, int height, int rowBytes, int payloadBytes, int crc32) {
            this.width = width;
            this.height = height;
            this.rowBytes = rowBytes;
            this.payloadBytes = payloadBytes;
            this.crc32 = crc32;
        }
    }

    private Argb8888BitmapStore() {
    }

    static boolean write(File file, Bitmap source) {
        if (file == null || source == null || source.isRecycled()
                || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        Bitmap pixels = source;
        Bitmap normalized = null;
        RandomAccessFile output = null;
        FileChannel channel = null;
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            int tightRowBytes = Math.multiplyExact(width, 4);
            if (source.getConfig() != Bitmap.Config.ARGB_8888
                    || source.getRowBytes() != tightRowBytes) {
                normalized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                new Canvas(normalized).drawBitmap(source, 0f, 0f,
                        new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
                pixels = normalized;
            }
            if (pixels.getRowBytes() != tightRowBytes) {
                return false;
            }
            int payloadBytes = Math.multiplyExact(tightRowBytes, height);
            long fileBytes = HEADER_BYTES + (long) payloadBytes;
            output = new RandomAccessFile(file, "rw");
            output.setLength(fileBytes);
            channel = output.getChannel();
            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_WRITE, 0L, fileBytes);
            mapped.order(ByteOrder.BIG_ENDIAN);
            mapped.putInt(MAGIC);
            mapped.putInt(VERSION);
            mapped.putInt(width);
            mapped.putInt(height);
            mapped.putInt(tightRowBytes);
            mapped.putInt(payloadBytes);
            mapped.putInt(0);
            mapped.putInt(0);
            mapped.position(HEADER_BYTES);
            pixels.copyPixelsToBuffer(mapped);
            int checksum = checksum(mapped, HEADER_BYTES, payloadBytes);
            mapped.order(ByteOrder.BIG_ENDIAN);
            mapped.putInt(24, checksum);
            mapped.force();
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (Throwable ignored) {
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
            if (normalized != null && !normalized.isRecycled()) {
                normalized.recycle();
            }
        }
    }

    static Bitmap decode(File file) {
        return decode(file, 1);
    }

    /** Maps and CRC-validates a raw colormap without allocating a Java Bitmap. */
    static MappedImage map(File file) {
        Header header = readRawHeader(file);
        if (header == null) {
            return null;
        }
        RandomAccessFile input = null;
        FileChannel channel = null;
        try {
            input = new RandomAccessFile(file, "r");
            channel = input.getChannel();
            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_ONLY, 0L, input.length());
            if (checksum(mapped, HEADER_BYTES, header.payloadBytes) != header.crc32) {
                channel.close();
                input.close();
                return null;
            }
            ByteBuffer payload = mapped.duplicate();
            payload.position(HEADER_BYTES);
            payload.limit(HEADER_BYTES + header.payloadBytes);
            payload = payload.slice().order(ByteOrder.nativeOrder());
            return new MappedImage(header.width, header.height, header.rowBytes,
                    input, channel, payload);
        } catch (Throwable ignored) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (Throwable ignoredClose) {
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignoredClose) {
                }
            }
            return null;
        }
    }

    static Bitmap decode(File file, int sampleSize) {
        Header header = readRawHeader(file);
        if (header == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = Math.max(1, sampleSize);
            return file == null ? null
                    : BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }
        return decodeRaw(file, header, Math.max(1, sampleSize));
    }

    static Info inspect(File file) {
        Header raw = readRawHeader(file);
        if (raw != null) {
            return new Info(true, raw.width, raw.height, raw.payloadBytes);
        }
        if (file == null || !file.isFile() || file.length() <= 0L) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        return bounds.outWidth > 0 && bounds.outHeight > 0
                ? new Info(false, bounds.outWidth, bounds.outHeight, file.length())
                : null;
    }

    static boolean isRaw(File file) {
        return readRawHeader(file) != null;
    }

    static boolean isUsable(File file) {
        Info info = inspect(file);
        return info != null && info.width > 0 && info.height > 0;
    }

    static File rawSibling(File legacyFile) {
        if (legacyFile == null) {
            return null;
        }
        String name = legacyFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(legacyFile.getParentFile(), base + ".argb8888");
    }

    static boolean migrate(File legacyFile, File rawFile) {
        if (rawFile == null) {
            return false;
        }
        if (isRaw(rawFile)) {
            return true;
        }
        Bitmap bitmap = decode(legacyFile);
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        File temp = new File(rawFile.getParentFile(), rawFile.getName() + ".tmp");
        try {
            if (!write(temp, bitmap)) {
                return false;
            }
            return replace(temp, rawFile);
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    static boolean replace(File temp, File target) {
        return temp != null && target != null && temp.isFile() && temp.renameTo(target);
    }

    private static Bitmap decodeRaw(File file, Header header, int sampleSize) {
        RandomAccessFile input = null;
        FileChannel channel = null;
        try {
            input = new RandomAccessFile(file, "r");
            channel = input.getChannel();
            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_ONLY, 0L, input.length());
            if (checksum(mapped, HEADER_BYTES, header.payloadBytes) != header.crc32) {
                return null;
            }
            if (sampleSize == 1) {
                Bitmap bitmap = Bitmap.createBitmap(
                        header.width, header.height, Bitmap.Config.ARGB_8888);
                if (bitmap.getRowBytes() != header.rowBytes) {
                    bitmap.recycle();
                    return null;
                }
                mapped.position(HEADER_BYTES);
                bitmap.copyPixelsFromBuffer(mapped);
                bitmap.prepareToDraw();
                return bitmap;
            }

            int outputWidth = Math.max(1, (header.width + sampleSize - 1) / sampleSize);
            int outputHeight = Math.max(1, (header.height + sampleSize - 1) / sampleSize);
            Bitmap output = Bitmap.createBitmap(
                    outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
            Bitmap sourceRow = Bitmap.createBitmap(
                    header.width, 1, Bitmap.Config.ARGB_8888);
            int[] fullRow = new int[header.width];
            int[] row = new int[outputWidth];
            try {
                for (int outputY = 0; outputY < outputHeight; outputY++) {
                    int sourceY = Math.min(header.height - 1, outputY * sampleSize);
                    int rowOffset = HEADER_BYTES + sourceY * header.rowBytes;
                    ByteBuffer rowPixels = mapped.duplicate();
                    rowPixels.position(rowOffset);
                    rowPixels.limit(rowOffset + header.rowBytes);
                    sourceRow.copyPixelsFromBuffer(rowPixels.slice());
                    sourceRow.getPixels(fullRow, 0, header.width,
                            0, 0, header.width, 1);
                    for (int outputX = 0; outputX < outputWidth; outputX++) {
                        int sourceX = Math.min(
                                header.width - 1, outputX * sampleSize);
                        row[outputX] = fullRow[sourceX];
                    }
                    output.setPixels(row, 0, outputWidth,
                            0, outputY, outputWidth, 1);
                }
            } finally {
                sourceRow.recycle();
            }
            output.prepareToDraw();
            return output;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (Throwable ignored) {
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Header readRawHeader(File file) {
        if (file == null || !file.isFile() || file.length() < HEADER_BYTES) {
            return null;
        }
        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(file));
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                return null;
            }
            int width = input.readInt();
            int height = input.readInt();
            int rowBytes = input.readInt();
            int payloadBytes = input.readInt();
            int crc32 = input.readInt();
            input.readInt();
            if (width <= 0 || height <= 0
                    || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || rowBytes != width * 4
                    || payloadBytes != rowBytes * height
                    || file.length() != HEADER_BYTES + (long) payloadBytes) {
                return null;
            }
            return new Header(width, height, rowBytes, payloadBytes, crc32);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static int checksum(ByteBuffer source, int offset, int length) {
        ByteBuffer bytes = source.duplicate();
        bytes.position(offset);
        bytes.limit(offset + length);
        CRC32 crc = new CRC32();
        byte[] chunk = new byte[Math.min(CRC_CHUNK_BYTES, Math.max(1, length))];
        while (bytes.hasRemaining()) {
            int count = Math.min(bytes.remaining(), chunk.length);
            bytes.get(chunk, 0, count);
            crc.update(chunk, 0, count);
        }
        return (int) crc.getValue();
    }
}
