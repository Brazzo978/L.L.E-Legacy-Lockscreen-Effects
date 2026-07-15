package com.codex.lle;

import android.os.Process;

final class Lle64Abi {
    private static final String ABI;

    static {
        if (Process.is64Bit()) {
            System.loadLibrary("lle64marker");
            ABI = nativeAbi();
            if (!"arm64-v8a".equals(ABI)) {
                throw new UnsatisfiedLinkError("Unexpected LLE64 ABI: " + ABI);
            }
        } else {
            // The unified Java trunk is also packaged as an armeabi-v7a-only APK.
            // Its absence of the ARM64 marker is intentional, not a startup failure.
            ABI = "armeabi-v7a";
        }
    }

    private Lle64Abi() {
    }

    static String verify() {
        return ABI;
    }

    private static native String nativeAbi();
}
