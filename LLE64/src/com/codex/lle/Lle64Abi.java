package com.codex.lle;

final class Lle64Abi {
    private static final String ABI;

    static {
        System.loadLibrary("lle64marker");
        ABI = nativeAbi();
        if (!"arm64-v8a".equals(ABI)) {
            throw new UnsatisfiedLinkError("Unexpected LLE64 ABI: " + ABI);
        }
    }

    private Lle64Abi() {
    }

    static String verify() {
        return ABI;
    }

    private static native String nativeAbi();
}
