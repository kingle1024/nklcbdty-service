package com.nklcbdty.api.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorsTest {

    @Test
    @DisplayName("normalize → L2 norm = 1")
    void normalize() {
        float[] v = {3, 4, 0};
        float[] n = Vectors.normalize(v);
        double s = 0;
        for (float x : n) s += x * x;
        assertEquals(1.0, Math.sqrt(s), 1e-5);
    }

    @Test
    @DisplayName("dot of unit vectors = cosine similarity, identical → 1, orthogonal → 0")
    void cosineSemantics() {
        float[] a = Vectors.normalize(new float[]{1, 1, 0});
        float[] b = Vectors.normalize(new float[]{1, 1, 0});
        float[] c = Vectors.normalize(new float[]{1, -1, 0});

        assertEquals(1.0f, Vectors.dot(a, b), 1e-5);
        assertEquals(0.0f, Vectors.dot(a, c), 1e-5);
    }

    @Test
    @DisplayName("toBytes/fromBytes round-trip 동일")
    void roundTrip() {
        float[] v = Vectors.normalize(new float[]{0.1f, -0.2f, 0.3f, -0.4f, 0.5f});
        byte[] bytes = Vectors.toBytes(v);
        assertEquals(v.length * 4, bytes.length);

        float[] back = Vectors.fromBytes(bytes);
        assertArrayEquals(v, back, 1e-6f);
    }

    @Test
    @DisplayName("길이 다른 벡터 dot → -1 (오류 신호)")
    void dotMismatch() {
        assertEquals(-1f, Vectors.dot(new float[]{1, 2}, new float[]{1, 2, 3}));
        assertEquals(-1f, Vectors.dot(null, new float[]{1, 2}));
    }
}
