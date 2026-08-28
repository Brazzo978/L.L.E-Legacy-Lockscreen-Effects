package com.codex.lle;

/** Pure-Java donor geometry used by the GLES renderer and host regression tests. */
final class LgPixelateMesh {
    static final int BASE_RESOLUTION = 100;

    final int width;
    final int height;
    final int rows;
    final int columns;
    final int vertexCount;
    final float[] positions;
    final float[] textureCoordinates;
    final float[] mosaicCoordinates;
    final float[] userAlpha;
    final float[] effectAlpha;

    private LgPixelateMesh(int width, int height, int rows, int columns,
            float[] positions, float[] textureCoordinates, float[] mosaicCoordinates,
            float[] userAlpha, float[] effectAlpha) {
        this.width = width;
        this.height = height;
        this.rows = rows;
        this.columns = columns;
        this.vertexCount = rows * columns * 6;
        this.positions = positions;
        this.textureCoordinates = textureCoordinates;
        this.mosaicCoordinates = mosaicCoordinates;
        this.userAlpha = userAlpha;
        this.effectAlpha = effectAlpha;
    }

    static LgPixelateMesh build(int requestedWidth, int requestedHeight) {
        int width = Math.max(1, requestedWidth);
        int height = Math.max(1, requestedHeight);
        int rows = BASE_RESOLUTION + 2;
        float shortSide = Math.min(width, height);
        float longSide = Math.max(width, height);
        int columns = Math.max(2, (int) (BASE_RESOLUTION * shortSide / longSide) + 2);
        int vertices = rows * columns * 6;
        float[] positions = new float[vertices * 2];
        float[] uv = new float[vertices * 2];
        float[] mosaicUv = new float[vertices * 2];
        float[] alpha = new float[vertices];
        float[] effect = new float[vertices];
        int vertex = 0;
        for (int row = 0; row < rows; row++) {
            float v0 = row / (float) rows;
            float v1 = (row + 1f) / rows;
            float y0 = v0 * height;
            float y1 = v1 * height;
            for (int column = 0; column < columns; column++) {
                float u0 = column / (float) columns;
                float u1 = (column + 1f) / columns;
                float x0 = u0 * width;
                float x1 = u1 * width;
                float flat1u = .6f * u0 + .4f * u1;
                float flat1v = .6f * v0 + .4f * v1;
                vertex = put(vertex, positions, uv, mosaicUv, alpha,
                        x0, y0, u0, v0, flat1u, flat1v);
                vertex = put(vertex, positions, uv, mosaicUv, alpha,
                        x0, y1, u0, v1, flat1u, flat1v);
                vertex = put(vertex, positions, uv, mosaicUv, alpha,
                        x1, y0, u1, v0, flat1u, flat1v);

                float flat2u = .6f * u1 + .4f * u0;
                float flat2v = .6f * v0 + .4f * v1;
                vertex = put(vertex, positions, uv, mosaicUv, alpha,
                        x0, y1, u0, v1, flat2u, flat2v);
                vertex = put(vertex, positions, uv, mosaicUv, alpha,
                        x1, y1, u1, v1, flat2u, flat2v);
                vertex = put(vertex, positions, uv, mosaicUv, alpha,
                        x1, y0, u1, v0, flat2u, flat2v);
            }
        }
        return new LgPixelateMesh(width, height, rows, columns,
                positions, uv, mosaicUv, alpha, effect);
    }

    void updateUserAlpha(float touchX, float touchY, float dragPx, float meshScale) {
        float scale = Math.max(1f, meshScale);
        float centerX = width * .5f;
        float centerY = height * .5f;
        float transformedX = centerX + (touchX - centerX) / scale;
        float transformedY = centerY + (touchY - centerY) / scale;
        float radiusSquared = Math.max(0f, dragPx / scale);
        radiusSquared *= radiusSquared;
        for (int vertex = 0; vertex < vertexCount; vertex += 6) {
            float first = triangleAlpha(vertex, transformedX, transformedY, radiusSquared);
            float firstEffect = 1f - first;
            userAlpha[vertex] = first;
            userAlpha[vertex + 1] = first;
            userAlpha[vertex + 2] = first;
            effectAlpha[vertex] = firstEffect;
            effectAlpha[vertex + 1] = firstEffect;
            effectAlpha[vertex + 2] = firstEffect;
            boolean secondIntersects = triangleIntersects(vertex + 3, transformedX,
                    transformedY, radiusSquared);
            float second = secondIntersects ? .5f : 1f;
            userAlpha[vertex + 3] = second;
            userAlpha[vertex + 4] = second;
            userAlpha[vertex + 5] = second;
            float secondEffect = secondIntersects ? .5f : 0f;
            effectAlpha[vertex + 3] = secondEffect;
            effectAlpha[vertex + 4] = secondEffect;
            effectAlpha[vertex + 5] = secondEffect;
        }
    }

    private float triangleAlpha(int firstVertex, float x, float y, float radiusSquared) {
        if (radiusSquared <= 0f) return 1f;
        for (int i = 0; i < 3; i++) {
            int offset = (firstVertex + i) * 2;
            float dx = positions[offset] - x;
            float dy = positions[offset + 1] - y;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < radiusSquared) return distanceSquared / radiusSquared;
        }
        return 1f;
    }

    private boolean triangleIntersects(int firstVertex, float x, float y,
            float radiusSquared) {
        if (radiusSquared <= 0f) return false;
        for (int i = 0; i < 3; i++) {
            int offset = (firstVertex + i) * 2;
            float dx = positions[offset] - x;
            float dy = positions[offset + 1] - y;
            if (dx * dx + dy * dy < radiusSquared) return true;
        }
        return false;
    }

    private static int put(int vertex, float[] positions, float[] uv, float[] mosaicUv,
            float[] alpha, float x, float y, float u, float v, float mosaicU,
            float mosaicV) {
        int offset = vertex * 2;
        positions[offset] = x;
        positions[offset + 1] = y;
        uv[offset] = u;
        uv[offset + 1] = v;
        mosaicUv[offset] = mosaicU;
        mosaicUv[offset + 1] = mosaicV;
        alpha[vertex] = 1f;
        return vertex + 1;
    }
}
