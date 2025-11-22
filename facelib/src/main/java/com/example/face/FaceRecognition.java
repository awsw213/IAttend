package com.example.face;

import android.graphics.Bitmap;
import android.graphics.Matrix;

public class FaceRecognition {
    private final TfLiteFaceEmbedder embedder;
    private float alpha = 13.9f;
    private float c0 = 0.30f;

    public FaceRecognition(TfLiteFaceEmbedder embedder) {
        this.embedder = embedder;
    }

    public float computeSimilarity(Bitmap a, Bitmap b) {
        if (embedder.isPairwise()) {
            return embedder.compare(a, b);
        } else {
            Bitmap aFlip = flipHorizontal(a);
            Bitmap bFlip = flipHorizontal(b);
            float[] e1o = embedder.embed(a);
            float[] e1f = embedder.embed(aFlip);
            float[] e2o = embedder.embed(b);
            float[] e2f = embedder.embed(bFlip);
            float[] e1 = average(e1o, e1f);
            float[] e2 = average(e2o, e2f);
            normalizeL2(e1);
            normalizeL2(e2);
            float cos = cosine(e1, e2);
            return sigmoid(alpha * (cos - c0));
        }
    }

    public boolean compare(Bitmap a, Bitmap b, float threshold) {
        return computeSimilarity(a, b) >= threshold;
    }

    public FaceRecognition setAlpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public FaceRecognition setCenter(float c0) {
        this.c0 = c0;
        return this;
    }

    private static float euclidean(float[] a, float[] b) {
        float s = 0f;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            s += diff * diff;
        }
        return (float) Math.sqrt(s);
    }

    private static float cosine(float[] a, float[] b) {
        float s = 0f;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static float sigmoid(float x) {
        double v = 1.0 / (1.0 + Math.exp(-x));
        return (float) v;
    }

    private static Bitmap flipHorizontal(Bitmap src) {
        Matrix m = new Matrix();
        m.preScale(-1f, 1f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private static float[] average(float[] a, float[] b) {
        float[] r = new float[a.length];
        for (int i = 0; i < a.length; i++) r[i] = 0.5f * (a[i] + b[i]);
        return r;
    }

    private static void normalizeL2(float[] v) {
        float s = 0f;
        for (float x : v) s += x * x;
        float n = (float) Math.sqrt(s);
        if (n == 0f) return;
        for (int i = 0; i < v.length; i++) v[i] /= n;
    }
}