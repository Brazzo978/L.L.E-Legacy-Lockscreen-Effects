package com.codex.lle;

/** On-demand Abstract Tiles capture used only by explicit ADB fidelity tests. */
interface DebugAbstractTilesCaptureRenderer {
    void captureDebugAbstractTilesFrame(String sequence, long phaseMs);
}
