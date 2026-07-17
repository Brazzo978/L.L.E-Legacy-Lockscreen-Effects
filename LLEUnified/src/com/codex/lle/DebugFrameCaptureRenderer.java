package com.codex.lle;

/** On-demand renderer framebuffer capture used by ADB fidelity tests. */
interface DebugFrameCaptureRenderer {
    void captureDebugAffordanceFrame(long phaseMs);
}
