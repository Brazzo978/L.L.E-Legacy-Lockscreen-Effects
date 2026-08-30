package com.codex.lle;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;

/** Source-level contract for the production-only ENB4 worker boundary. */
public final class RippleInkN3NativeWiringTest {
    public static void main(String[] args) throws Exception {
        File root = new File(System.getProperty("lle.repoRoot", "")).getCanonicalFile();
        require(root.isDirectory(), "repo root is required");
        String pipeline = read(root, "LLEUnified/src/com/codex/lle/RippleInkPortFluidPipeline.java");
        String renderer = read(root, "LLEUnified/src/com/codex/lle/RippleInkPortGlesRenderer.java");
        String bridge = read(root, "LLEUnified/src/com/codex/lle/N3RippleInkWorkerNative.java");
        String build = read(root, "LLEUnified/build-arm64.ps1");

        require(bridge.contains("System.loadLibrary(\"lleN3RippleInk\")"),
                "native bridge does not load its dedicated library lazily");
        require(bridge.contains("static native byte[] nativeStep("),
                "native bridge has no N-1 velocity result API");
        require(pipeline.contains("private final boolean nativeWorkerRequired = isAndroidRuntime()"),
                "pipeline does not distinguish Android production from host tests");
        require(pipeline.contains("throw new IllegalStateException(nativeWorkerFailureDetail)"),
                "native worker failure is not fail-closed");
        // Formatting the bridge call is intentionally not part of the contract. Collapse only
        // whitespace before proving the ENB4 N-1 sequencing relationship.
        String compactPipeline = pipeline.replaceAll("\\s+", " ");
        int step = compactPipeline.indexOf("velocityRgba = stepNativeWorker(activePreset, "
                + "snapshot.currentX, snapshot.currentY, snapshot.previousX, snapshot.previousY);");
        int upload = compactPipeline.indexOf(
                "sink.uploadVelocity(velocityRgba, fluidWidth, fluidHeight);");
        int scalar = compactPipeline.indexOf("if (!useNativeWorker) { advanceVelocity(activePreset, "
                + "snapshot.currentX, snapshot.currentY, snapshot.previousX, snapshot.previousY);");
        require(step >= 0 && upload > step && scalar > upload,
                "N-1 native velocity must upload before density passes and scalar stay host-only");
        require(renderer.contains("fluidPipeline.isNativeWorkerReadyForProduction()"),
                "renderer does not reject an unavailable Android worker");
        require(renderer.contains("fluidPipeline.releaseNativeWorker();"),
                "renderer does not join/destroy worker at GL teardown");
        int uploadStart = renderer.indexOf("public void uploadVelocity(byte[] rgba, int width, int height)");
        int uploadEnd = renderer.indexOf("public void advectDensity(", uploadStart);
        require(uploadStart >= 0 && uploadEnd > uploadStart,
                "renderer velocity upload method is missing");
        String velocityUpload = renderer.substring(uploadStart, uploadEnd);
        require(velocityUpload.contains("GLES20.glTexImage2D(")
                        && !velocityUpload.contains("GLES20.glTexSubImage2D("),
                "ENB4 velocity Update must redefine, not sub-update, its RGBA texture each tick");
        require(build.contains("liblleN3RippleInk.so")
                        && build.contains("lle_n3_ink_worker.c")
                        && build.contains("lle_n3_ink_jni.c"),
                "ARM64 build does not compile/package the N3 worker");
        System.out.println("RippleInkN3NativeWiringTest: PASS");
    }

    private static String read(File root, String relative) throws Exception {
        File file = new File(root, relative);
        require(file.isFile(), "missing source: " + relative);
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private RippleInkN3NativeWiringTest() {
    }
}
