package com.codex.lle;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/** Exact geometry emitted by Samsung's ARM32 Brilliant Cut CreateGeometry(). */
public final class BrilliantCutStockGeometry {
    public static final int PORTRAIT_SPECIAL = 0;
    public static final int PORTRAIT_NORMAL = 1;
    public static final int LANDSCAPE_SPECIAL = 2;
    public static final int LANDSCAPE_NORMAL = 3;

    public static final String ORACLE_SHA256 =
            "694E860290A277570992142E965B858DBB8D75FF168030AC0661EDB01B426EC2";
    public static final String PAYLOAD_SHA256 =
            "37F49EC15DF52E768610C34712E5F31175A4EEF194CEFC20AA4E46533A1C4616";

    /**
     * Stock vertices are already an ordered GL_TRIANGLES stream. Plane
     * boundaries remain available because Samsung computes normals and alpha
     * per plane even though the final draw is a single glDrawArrays call.
     */
    public static final class Mesh {
        public final int geometryType;
        public final int vertexCount;
        public final int triangleCount;
        public final float[] xyz;
        public final short[] indices;
        public final short[] planeFirstVertices;
        public final byte[] planeVertexCounts;

        private Mesh(int geometryType, int vertexCount, float[] xyz,
                short[] indices, short[] planeFirstVertices,
                byte[] planeVertexCounts) {
            this.geometryType = geometryType;
            this.vertexCount = vertexCount;
            this.triangleCount = vertexCount / 3;
            this.xyz = xyz;
            this.indices = indices;
            this.planeFirstVertices = planeFirstVertices;
            this.planeVertexCounts = planeVertexCounts;
        }
    }

    private static final int MAGIC_BCM1 = 0x314d4342;
    private static volatile Mesh[] cachedMeshes;

    private BrilliantCutStockGeometry() {
    }

    public static Mesh get(int geometryType) {
        if (geometryType < 0 || geometryType > 3) {
            throw new IllegalArgumentException("Unknown Brilliant Cut geometry: " + geometryType);
        }
        Mesh[] meshes = cachedMeshes;
        if (meshes == null) {
            synchronized (BrilliantCutStockGeometry.class) {
                meshes = cachedMeshes;
                if (meshes == null) {
                    meshes = decodeMeshes();
                    cachedMeshes = meshes;
                }
            }
        }
        return meshes[geometryType];
    }

    private static Mesh[] decodeMeshes() {
        byte[] compressed = Base64.decode(PACKED_BCM1_GZIP_BASE64, Base64.DEFAULT);
        byte[] payload = inflate(compressed);
        ByteBuffer input = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        if (input.getInt() != MAGIC_BCM1) {
            throw new IllegalStateException("Invalid Brilliant Cut geometry magic");
        }
        int geometryCount = input.getInt();
        if (geometryCount != 4) {
            throw new IllegalStateException("Invalid Brilliant Cut geometry count: " + geometryCount);
        }

        Mesh[] meshes = new Mesh[geometryCount];
        for (int geometry = 0; geometry < geometryCount; geometry++) {
            int geometryType = input.getInt();
            int planeCount = input.getInt();
            int vertexCount = input.getInt();
            if (geometryType < 0 || geometryType >= geometryCount || meshes[geometryType] != null) {
                throw new IllegalStateException("Invalid Brilliant Cut geometry id: " + geometryType);
            }
            if (planeCount <= 0 || vertexCount <= 0 || vertexCount > 0xffff) {
                throw new IllegalStateException("Invalid Brilliant Cut mesh dimensions");
            }

            byte[] planeVertexCounts = new byte[planeCount];
            input.get(planeVertexCounts);
            short[] planeFirstVertices = new short[planeCount];
            int firstVertex = 0;
            for (int plane = 0; plane < planeCount; plane++) {
                int count = planeVertexCounts[plane] & 0xff;
                if (count == 0 || count % 3 != 0) {
                    throw new IllegalStateException("Invalid Brilliant Cut plane arity: " + count);
                }
                planeFirstVertices[plane] = (short) firstVertex;
                firstVertex += count;
            }
            if (firstVertex != vertexCount || vertexCount % 3 != 0) {
                throw new IllegalStateException("Brilliant Cut vertex/plane mismatch");
            }

            float[] xyz = new float[vertexCount * 3];
            for (int component = 0; component < xyz.length; component++) {
                xyz[component] = input.getFloat();
            }
            short[] indices = new short[vertexCount];
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                indices[vertex] = (short) vertex;
            }
            meshes[geometryType] = new Mesh(geometryType, vertexCount, xyz,
                    indices, planeFirstVertices, planeVertexCounts);
        }
        if (input.hasRemaining()) {
            throw new IllegalStateException("Trailing Brilliant Cut geometry bytes: " + input.remaining());
        }
        return meshes;
    }

    private static byte[] inflate(byte[] compressed) {
        try {
            GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
            ByteArrayOutputStream output = new ByteArrayOutputStream(24904);
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            input.close();
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to decode Brilliant Cut stock geometry", error);
        }
    }

    private static final String PACKED_BCM1_GZIP_BASE64 =
            "H4sIAAAAAAAC/+2be4wV1R3Hp4KXR1BZq4LNotuLb1TU8YGyd0YtxgraYgUfVDeKZkpKfRIlVk2p0mYxLQnqirM2GyGprRux" +
            "tiU2BXamNlokzhqNopYUaiqaWCNqa6o2xO357ezc+cw5c4btP03/uGtYfn75nN953DNnzsz9nvO/dsmpY530p0f9OXI/xxkz" +
            "pjYm/ZkwEmX/n//U5B9HolpRr/yp8W/5VRuuQyWpTRipUn7VJtS0VNuTxfG2ZH9fGjreOSJeGhwwHLuuG382tMaTmExPODc+" +
            "yz3C4G16f/h81BVcXKl3BR9F68MhT4/JOM4tketOMeIic0rUFx5VEud8bxhuGUx2e3pMfkfS3ugJTzZ46h8OLfO6gkl+le66" +
            "D3o94TeMmEwQRN768CAjJr8recELgsWV+qrwRtXGFUa8J7mhGdvKst6+cKy/NDjD0Ge4G7xOd9vIZ/SWN5h8YsS2smR6w7P9" +
            "Ge6pRl1kXklm+j3hRF/nqTvODX5fONVgmKcnPE3pzxs5ybBf5Iv9fcy7OXjSYKjvHZrq94ZrDIafNXNyfMgsDxarnBuNNnAe" +
            "krGVZdtseXrDCd7lwb1GzmJd+6lr5zKDL+a5KpI6TL5wrUW94ccGX7wGC9dpk+eaQN3Gq35Ene46oy7mKTLldakxiwaT7xk8" +
            "GduaSYY6czImo9ai+NogauhxfzgY1d0pRlkyHw5dH/0iPH5A4sHkoegAZ76Rh7otJ3XyjG3jQ4btWRVeI7yRh/r25P6oPzza" +
            "yEOddbEs61J9HXDdf27R5yR5G8O6yJNhG8gX538+98iz3sfCdY0vTlvWqZelXrxOy5nxzlNqTXuyofNkWG9veHhDfb6RWVeu" +
            "q/Z7defNAZPJddZbbEPOBEFNrY2Hb9YZ6nX3DW9zeKmnM9RlDRtM7jZi9pdlyRTXt5wnwzWTPBmu8+TJsM3kqcu61Rtua+gM" +
            "9bGOr+Jwts4wJsP7ThAcJvrI3DjMP8YdM6Dz1LuCQ/zuYJfBMA91tpMMdfaXTFHP5wCZ4pzJ28mYDNvPmAzn5PrQ9z8cmh7p" +
            "/WJZMtTVjx8Ef4r0uCf8lv/Z0A8jvSyZ8c5tas/TFus8mbPcM/2Z7nGxzpPpCw9R95yvxzpPZlfykTfDPT7WeTJz3B94ryTT" +
            "Y50n0+a0q+vlAYMnw3WDfJHJx588mU73YzU3zjN4MvwsyDMmY6uX6+FrycqBg915Rl+oO84fZG7GZvtzvdj3cr04hjlDvc25" +
            "2BvvnBObeXJ9MPmJ6tMCg6G+J5kTtTmfRFX6eOdx9exzYqzHZFx3s9oP/NXgqa8Px8Xq+jfyUL88+Hu0NDivUlfPkXFveLzB" +
            "UN+enKz+7jAY6uq6jteHYwyGuvCXB+dEZXky/Sx3v/i55PHIbE+ucxxs+gVBV1R3pho6x5kMdds8HA1PhjrnPxnq3FORod4V" +
            "vBllY0WGOsfEpnPMyVDvDRtxfl3nMZntyffjg927jDzkZY4MJpOM9lNnHxmT4b56hvvVOHvW3jt0StwTBn563b0WjXcW+TpD" +
            "fVtyeFNnTKY3/DzKdMZktid/jHrCO4yYjON0RzPdJUYe8nV3a1NnTEbyZDpjncnq0vlMH0za0Zd2C3PxQM7kMZlxTreat9cb" +
            "8THu9EZZWerkV4UNvEvJYzK2dzt831LMk7+T4buXNmdvU2dMhu9JyFBX15Gf6YzJ9IXHqf3h7SVxzsxwL/D3Di0oyZPzag0u" +
            "zc/3MMzDa2Gsc2y8d2i+MSepB4E8U59dyfB9o42nzveENp05maf4jrE4n7PY/h6SfPk7RjLUbfOZDOee7f7YF56u3t9eZdxD" +
            "qat8suYYjK5nse1+XcVk+nNJFGXxHPebirnG2DOwLBnqtjzU+8OfljLU1V6ulKG+I7l6oCe8zWCos/3ca1EnT4Y693jU2R4V" +
            "z87iujPLWxx8u7IsmWLOZ9Vz6xKDoc6c1LW2zc7bljOFdrq/8rKYOfncYdsD8/mFvK1t5At9RBtmuqf7ZW2mzjyMtyU3qvfP" +
            "VxrPX2SYh/yX1J971J+dKqgNf1kz8rv5tVBtzPAXNcP/1fglTy2DC1/+1Eq+Bao1v0SqlbDp73SByG6kHfF93XcPxwvaL4yC" +
            "4LamnjHtG99o3jzJ2PSZq6+fPa0+12A2DRzVvImRef3WvzTmLzyjkqFO/oW1tyj9XIOh/v6sPepDvtpgqG9Y+e4+9YuWHOSr" +
            "yVKpq3Hz96VLPKXtVkt8UWnZMv3QrW3+e3vOMWIy7AsZ2/iwLvKi39d9iFGWn8XNM9d50+pHGAzzkLmj/Q3vnuVTKusiI3oQ" +
            "TKxkRlPX67fu9Vz3fS/L88zmcfusq4w5YfVv1bg95+k5bfqhWyf4Z8/5mVHvaBiJe8NVBsM+st7imBRzZnkkPnvOSiPnCatf" +
            "9SbVF++Dyesibysr8Un1ew1m+rqdqu+uwdh0iYcmrxl+WbhoSewd0LZmoIzPGBsv+s+7uzfpMRmZS+0fvmTEetmT6psinWnf" +
            "+Kmn+h7pjC0/+Q0rf+w91P1ZVJWHTLq2vPlfMU/3HxPrzH3/Olhd+6fGGTN3zolxWZ6sLBlbWYnf3nlpKZPpI2ujwUxfN9C8" +
            "aZP5/OBF6iHpHINnO20Mc5K5Z8KGpj4axl94eynzctf5jWn1LoOx6S/1n9CZxXM/eKexevkUY55ct/BH3kn1Bwydc4OMbS6R" +
            "oV7f+utGre1zg6G+a9bqgd7w9wZDne0nc9ldq7xly9c29OuUfbExXCvIFNecfC0ic+COR71Xd/aMmjlwxwzPX3iBV3Vf69y4" +
            "dpPrvlq59jInGerMw9jWniKT62rMB/68xzPiYl/avbC/v1PXOQ5kqPMzIkOdnzsZ6o+83DfwlbY2Iw91tp8M+05mw8qHVe63" +
            "jPGhvqD9O+qL3EnGZ0dd7QlVPNVgqGt71Kb+5NuzorlzFpTtV0vLkrGV5V7aVtc/jn5OMZ6hsywZW39tDPWbZ34cXbfwA2Pc" +
            "qHPM+UzxyMDuSEw7mf7enkV+FWNrv41hTjIsq9f7dP/5pYxNzz47m/7qvMmqL+2V40bGVlb0f+/5wuCZh4xN/93aySp+xojJ" +
            "SBwEDxt5yO+e90K0evkVw/G4D/aPj227tJLR68ryS/zJzktKmUxnfraH+Xd2/SbauvlBg6HOeWjTuYaQoc56GdvWq/vfnhwrv" +
            "ZH1a2P/dxv6lwY6k3+Z0BHPW/7pQNWYMycZ6nr+srG18WrPEKUvmmSfszN6cc9NnWV6rW3VyD39CfX3/EqeY2XTmYf9YtuY" +
            "nwx15tTHIRtbtoG6xFs3vzisJ10T1fx8Kqoqy3Egw7JVY5XpMidd99HKMeH+yqYzD/PLelhre9ZgCvWCYU59HLLxkfik+t9K" +
            "mUxnTraHPBntmmruGdge8qxrzctfqPV2Yqwz1Ns3XhHd1z3JYKizLtu+lzwZ6m8cvVEZG080GOp8XiBDnftzlmVdu+eNV/9+" +
            "pVEvdT53kKHOel9Y+/KW7HnElocMn1/IvD+rr/nSmvxld4XRtPodlTrrtfWXPBnqr9+aRE/31w199uovq/3AXIPhnBkNw3rJ" +
            "yPx8ZvO8WJ+rzGnTbW3Qc05pu7OUyfSbZ05ofvFChjo/IzLUOW7MP/J+2Mi538jJh8n/65MP2dvv2khQax5/SN+wZ2/LWycf" +
            "WicfWicfWicfWicfWicfWicfWicfWicfWicfWicfWicfWicfWicfWicfWicfWicfRn/y4ZVkmZpXR448n65Q70NObsbjnYl+" +
            "GZPpfMfCmIzEbc5uT3/fQkc485OhbnufwzzF9yp5vRJn+5ylwZ1xdh8nQ93WLzJ89mFMZu9Qp/oC7d1GWb1196xmrF5OlzKZ" +
            "zjyM+TkWn8XyshLPcX1jjpGhbquLDOckmeJcLX8etM1ntlPi7ckOo816+zOmuDbmjG3NZFmJZ7iHGmss9b7w2viV5MJKJn15" +
            "Ot/gbWs7Gdt7IZ7EIFM86XGH2mOcaTBFfYXaax1lMEV9har3Ha+MyfSxzjKl/dJgqHM/T31pIIbU7kpG6tqVrPDMesufF5iT" +
            "TFHP85Ox1UtGb8+qcFopk+kSv5ZsbJQxmb49ucmf6j64RWeocx9u0yWnen43GOoSTw9eHyhjMp1tY/7iPn+Fr04YRGVtyHTW" +
            "y7LFZ4EVfqf7RGn+TNdzZrqeJ7umGJOha58xmeIzSD6f+d61ON+K63N2T9HX8+weyvsX71Nj/n9OEeSDlzp50licOeLQKRvs" +
            "9DSAyZTp4oYSV5TOSJy6+IuMOGrEaVXFUC/y5/qpY73IUBeHvDjldUbX05MBVfoSXxz6+9JTx6tdT08NlMUXlZa16eKWFyey" +
            "HpNhX8jYxod1kU8d+x1GWX4W4pYU16TOMA8ZcbmL272qLjKpq7+jkhlNXeLYF+e+xKlLv7ouGyOuUXGP6jltujjwxYmv5xwN" +
            "kzr/05gM+8h62U7yxTwrS3OKO1dculUM6yJvK5ueAugw8otbWFzDOmPT0xMBaSwuVnGz6jwZG5+eAkh1xmTE4Z/NJcbkU8d+" +
            "h8GIi1vc3Dpjy09e3CPiIqnKQyY9HWC2x8akpwBMRpz24rgfdvMMnwIwGZYlYyubnhQwGerpJtpkRE8d/UVGHC/ifNF5ttPG" +
            "MCcZ0dNTAqNn0tMARUZcNOKm0RmbLnHqxHEccU+Ji0qfJ+LGF1e+rhfnRs7Y5hIZ6uJiEjeTzlAXJ5U4qnSGOttPRhzp4kzX" +
            "r1P2xcZwrSBDnWsRGXHjiyt/tIy468VlX3VfE4e5OM2r1l7mJEOdeRjb2kOGurhYxc2qx2TEjS+ufF3nOJChzs+IDHV+7mSo" +
            "i0tQ3II6Q53tJ8O+kxFXcOoOLjLUxfEuznedoS6OenHWm0yucz5QF/e+uPj1/aqtLBlbWe6lbXWJU10c66aelyVj66+NoS5u" +
            "bXFt6wx1jjmfKeQBK3X3y95sUelzBxlb+20McxaZvCyZ9KRA9ryTMzY9dftX6+L2F9d/1biRsZVNHf6mzjxkbLq49MWtb8Y5" +
            "kzr8y/LkvLi1xbUtsbjQxY1exVBn/tTNbraNOvOzPcwvbn9x/esMdc5Dm841hAx11svYtl6Ja13c6xKnLv2OkS+45EVnGpOh" +
            "nrrQOyrHnDnJUGd+29jaeHHIi1NeYnHmi/tb18UNnuniZk9d7XaeY2XTmUcb52bbmJ8MdeZkWY4t20A9dchn8/OpSNzuVWU5" +
            "DmRY1jZW1MXJn81J25hwf2XTmYf5xS2frYdkinzOMCf7wvFJnc9mf6kzJ9tTHJ+cYd+5Z2B7yLMucVyL81pnqIvTWxzfOkO9" +
            "WFf5vreYJ2eoi0tf3Po6Q53PC2Soc3/OsqxLHPWps77IUOdzBxnq+jNO6u635yky+fOLzqRu9CIvjnRxplfreb22/pInQ11c/" +
            "eLu13Vxy4trXmc4Z0bDsF4yqdvfvF6Y06bb2sCcqZPfLEtdxjZ19xcZ6vyMdCbTOW56/rK6/gMz1sQvSGEAAA==";
}
