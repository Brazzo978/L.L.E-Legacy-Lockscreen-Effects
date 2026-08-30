package com.codex.lle;

/** Host-only mesh contract checks for G2 Pixelate. */
public final class LgPixelateMeshTest {
    private LgPixelateMeshTest() { }

    public static void main(String[] args) {
        LgPixelateMesh mesh = LgPixelateMesh.build(1080, 2340);
        require("donor rows", mesh.rows == 102);
        require("donor columns", mesh.columns == 48);
        require("six vertices per cell", mesh.vertexCount == 102 * 48 * 6);
        near("triangle one flat u", .4f / 48f, mesh.mosaicCoordinates[0]);
        near("triangle one flat v", .4f / 102f, mesh.mosaicCoordinates[1]);
        require("flat coordinate repeated", mesh.mosaicCoordinates[0]
                == mesh.mosaicCoordinates[2]);
        mesh.updateUserAlpha(540f, 1170f, 400f, 3f);
        boolean sawMasked = false;
        boolean sawHalf = false;
        boolean sawOpaque = false;
        boolean sawActive = false;
        boolean sawInactive = false;
        boolean sawPartialEffect = false;
        for (int i = 0; i < mesh.userAlpha.length; i++) {
            float alpha = mesh.userAlpha[i];
            float effect = mesh.effectAlpha[i];
            require("mask finite", !Float.isNaN(alpha) && !Float.isInfinite(alpha));
            require("mask range", alpha >= 0f && alpha <= 1f);
            require("effect mask range", effect >= 0f && effect <= 1f);
            sawMasked |= alpha < 1f;
            sawHalf |= alpha == .5f;
            sawOpaque |= alpha == 1f;
            sawActive |= effect == 1f;
            sawInactive |= effect == 0f;
            sawPartialEffect |= effect > 0f && effect < 1f;
        }
        require("radial mask exists", sawMasked && sawHalf && sawOpaque);
        require("effect is locally clipped", sawActive && sawInactive && sawPartialEffect);
    }

    private static void require(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    private static void near(String label, float expected, float actual) {
        if (Math.abs(expected - actual) > .0001f) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
