package com.eb.cdcrag.embedding;

public final class VectorLiterals {
    private VectorLiterals() {}

    public static String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
