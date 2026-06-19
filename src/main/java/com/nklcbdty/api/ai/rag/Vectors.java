package com.nklcbdty.api.ai.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 임베딩 벡터 유틸. float[] ↔ byte[] 변환 + 코사인 유사도.
 *
 * <p>모든 벡터는 단위벡터(L2-norm=1)로 정규화되어 저장된다고 가정한다.
 * → 코사인 유사도 = 내적(dot product) 으로 계산 가능 → 5~10배 빠름.</p>
 */
public final class Vectors {

    private Vectors() {}

    public static float[] normalize(float[] v) {
        double s = 0;
        for (float x : v) s += x * x;
        double n = Math.sqrt(s);
        if (n < 1e-12) return v.clone();
        float inv = (float) (1.0 / n);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] * inv;
        return out;
    }

    /** 단위벡터 가정 — dot product = cosine similarity */
    public static float dot(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return -1f;
        float s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    public static byte[] toBytes(float[] v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) bb.putFloat(f);
        return bb.array();
    }

    public static float[] fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new float[0];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int n = bytes.length / 4;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = bb.getFloat();
        return out;
    }
}
