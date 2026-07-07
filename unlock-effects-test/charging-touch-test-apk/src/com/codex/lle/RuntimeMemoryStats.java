package com.codex.lle;

import android.os.Debug;
import android.os.SystemClock;

import java.util.Locale;

final class RuntimeMemoryStats {
    final long wallTimeMs;
    final long uptimeMs;
    final int totalPssKb;
    final int privateDirtyKb;
    final int nativePssKb;
    final int dalvikPssKb;
    final int otherPssKb;
    final int javaHeapKb;
    final int nativeHeapKb;
    final int graphicsKb;
    final int codeKb;
    final int stackKb;
    final int systemKb;
    final int swapKb;
    final long nativeAllocatedKb;
    final long javaUsedKb;
    final long javaTotalKb;
    final long javaMaxKb;

    private RuntimeMemoryStats(
            long wallTimeMs,
            long uptimeMs,
            int totalPssKb,
            int privateDirtyKb,
            int nativePssKb,
            int dalvikPssKb,
            int otherPssKb,
            int javaHeapKb,
            int nativeHeapKb,
            int graphicsKb,
            int codeKb,
            int stackKb,
            int systemKb,
            int swapKb,
            long nativeAllocatedKb,
            long javaUsedKb,
            long javaTotalKb,
            long javaMaxKb) {
        this.wallTimeMs = wallTimeMs;
        this.uptimeMs = uptimeMs;
        this.totalPssKb = totalPssKb;
        this.privateDirtyKb = privateDirtyKb;
        this.nativePssKb = nativePssKb;
        this.dalvikPssKb = dalvikPssKb;
        this.otherPssKb = otherPssKb;
        this.javaHeapKb = javaHeapKb;
        this.nativeHeapKb = nativeHeapKb;
        this.graphicsKb = graphicsKb;
        this.codeKb = codeKb;
        this.stackKb = stackKb;
        this.systemKb = systemKb;
        this.swapKb = swapKb;
        this.nativeAllocatedKb = nativeAllocatedKb;
        this.javaUsedKb = javaUsedKb;
        this.javaTotalKb = javaTotalKb;
        this.javaMaxKb = javaMaxKb;
    }

    static RuntimeMemoryStats capture() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        Runtime runtime = Runtime.getRuntime();
        long javaTotal = runtime.totalMemory() / 1024L;
        long javaFree = runtime.freeMemory() / 1024L;
        return new RuntimeMemoryStats(
                System.currentTimeMillis(),
                SystemClock.uptimeMillis(),
                info.getTotalPss(),
                info.getTotalPrivateDirty(),
                info.nativePss,
                info.dalvikPss,
                info.otherPss,
                statKb(info, "summary.java-heap", info.dalvikPss),
                statKb(info, "summary.native-heap", info.nativePss),
                statKb(info, "summary.graphics", -1),
                statKb(info, "summary.code", -1),
                statKb(info, "summary.stack", -1),
                statKb(info, "summary.system", -1),
                statKb(info, "summary.total-swap", -1),
                Debug.getNativeHeapAllocatedSize() / 1024L,
                Math.max(0L, javaTotal - javaFree),
                javaTotal,
                runtime.maxMemory() / 1024L);
    }

    String summary(String label, int effect) {
        StringBuilder text = new StringBuilder();
        text.append(label).append('\n');
        text.append("Effect: ").append(OverlayPrefs.effectLabel(effect)).append('\n');
        text.append("PSS: ").append(formatMb(totalPssKb));
        if (swapKb >= 0) {
            text.append(" | Swap: ").append(formatMb(swapKb));
        }
        text.append('\n');
        text.append("Java: ").append(formatMb(javaHeapKb))
                .append(" | Native: ").append(formatMb(nativeHeapKb));
        if (graphicsKb >= 0) {
            text.append(" | Graphics: ").append(formatMb(graphicsKb));
        }
        text.append('\n');
        text.append("Runtime Java used: ").append(formatMb(javaUsedKb))
                .append(" / ").append(formatMb(javaMaxKb))
                .append(" | Native alloc: ").append(formatMb(nativeAllocatedKb));
        return text.toString();
    }

    static String csvHeader() {
        return "timestamp_ms,effect,effect_name,preload_ms,attach_ms,warm_ms,total_ms,"
                + "total_pss_kb,private_dirty_kb,java_heap_kb,native_heap_kb,"
                + "graphics_kb,code_kb,stack_kb,system_kb,swap_kb,"
                + "native_alloc_kb,java_used_kb,java_total_kb,java_max_kb,status\n";
    }

    static String formatMb(long kb) {
        if (kb < 0L) {
            return "-";
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    private static int statKb(Debug.MemoryInfo info, String key, int fallback) {
        try {
            String value = info.getMemoryStat(key);
            if (value == null || value.length() == 0) {
                return fallback;
            }
            return Integer.parseInt(value);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
