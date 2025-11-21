package com.example.face;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.media.FaceDetector;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TfLiteFaceEmbedder {
    private final Interpreter interpreter;
    private int inputWidth = 112;
    private int inputHeight = 112;
    private int embSize = 128;
    private final boolean pairwise;

    public TfLiteFaceEmbedder(Context context, String modelAssetName) {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Runtime.getRuntime().availableProcessors());
        this.interpreter = new Interpreter(loadModelBuffer(context, modelAssetName), options);
        int inCount = interpreter.getInputTensorCount();
        this.pairwise = inCount >= 2;
        try {
            int[] inShape = interpreter.getInputTensor(0).shape();
            if (inShape.length >= 4) {
                inputHeight = inShape[1];
                inputWidth = inShape[2];
            }
            int[] outShape = interpreter.getOutputTensor(0).shape();
            if (outShape.length == 2) embSize = outShape[1];
            if (outShape.length == 1) embSize = outShape[0];
        } catch (Throwable ignored) {}
    }

    public float[] embed(Bitmap bitmap) {
        if (pairwise) throw new IllegalStateException("pairwise model");
        Bitmap cropped = cropFaceOrCenter(bitmap);
        Bitmap resized = Bitmap.createScaledBitmap(cropped, inputWidth, inputHeight, true);
        ByteBuffer input = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * 3);
        input.order(ByteOrder.nativeOrder());
        int[] pixels = new int[inputWidth * inputHeight];
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        int idx = 0;
        for (int y = 0; y < inputHeight; y++) {
            for (int x = 0; x < inputWidth; x++) {
                int p = pixels[idx++];
                float r = (((p >> 16) & 0xFF) - 127.5f) / 128.0f;
                float g = (((p >> 8) & 0xFF) - 127.5f) / 128.0f;
                float b = ((p & 0xFF) - 127.5f) / 128.0f;
                input.putFloat(r);
                input.putFloat(g);
                input.putFloat(b);
            }
        }
        input.rewind();
        float[][] output = new float[1][embSize];
        interpreter.run(input, output);
        float[] emb = output[0];
        normalizeL2(emb);
        return emb;
    }

    public float compare(Bitmap a, Bitmap b) {
        if (pairwise) {
            ByteBuffer ia = preprocess(a);
            ByteBuffer ib = preprocess(b);
            Object[] inputs = new Object[]{ia, ib};
            float[] out = new float[1];
            java.util.Map<Integer, Object> outputs = new java.util.HashMap<>();
            outputs.put(0, out);
            interpreter.runForMultipleInputsOutputs(inputs, outputs);
            return out[0];
        } else {
            float[] e1 = embed(a);
            float[] e2 = embed(b);
            float d = euclidean(e1, e2);
            return 1f / (1f + d);
        }
    }

    public boolean isPairwise() {
        return pairwise;
    }

    private ByteBuffer preprocess(Bitmap bitmap) {
        Bitmap cropped = cropFaceOrCenter(bitmap);
        Bitmap resized = Bitmap.createScaledBitmap(cropped, inputWidth, inputHeight, true);
        ByteBuffer input = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * 3);
        input.order(ByteOrder.nativeOrder());
        int[] pixels = new int[inputWidth * inputHeight];
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        int idx = 0;
        for (int y = 0; y < inputHeight; y++) {
            for (int x = 0; x < inputWidth; x++) {
                int p = pixels[idx++];
                float r = (((p >> 16) & 0xFF) - 127.5f) / 128.0f;
                float g = (((p >> 8) & 0xFF) - 127.5f) / 128.0f;
                float b = ((p & 0xFF) - 127.5f) / 128.0f;
                input.putFloat(r);
                input.putFloat(g);
                input.putFloat(b);
            }
        }
        input.rewind();
        return input;
    }

    public void close() {
        interpreter.close();
    }

    private static ByteBuffer loadModelBuffer(Context context, String modelAssetName) {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelAssetName);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (Exception e1) {
            try {
                InputStream is = context.getAssets().open(modelAssetName);
                byte[] bytes = readAllBytes(is);
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
                buffer.order(ByteOrder.nativeOrder());
                buffer.put(bytes);
                buffer.rewind();
                return buffer;
            } catch (Exception e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    private static void normalizeL2(float[] v) {
        float sum = 0f;
        for (float f : v) sum += f * f;
        float norm = (float) Math.sqrt(sum);
        if (norm == 0f) return;
        for (int i = 0; i < v.length; i++) v[i] /= norm;
    }

    private static float euclidean(float[] a, float[] b) {
        float s = 0f;
        for (int i = 0; i < a.length; i++) {
            float d = a[i] - b[i];
            s += d * d;
        }
        return (float) Math.sqrt(s);
    }

    private static Bitmap centerCrop(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;
        return Bitmap.createBitmap(src, x, y, size, size);
    }

    private static Bitmap cropFaceOrCenter(Bitmap src) {
        try {
            Bitmap work = src;
            int maxDim = Math.max(work.getWidth(), work.getHeight());
            if (maxDim > 512) {
                float scale = 512f / maxDim;
                int nw = Math.max(1, Math.round(work.getWidth() * scale));
                int nh = Math.max(1, Math.round(work.getHeight() * scale));
                work = Bitmap.createScaledBitmap(work, nw, nh, true);
            }
            Bitmap work565 = work.copy(Bitmap.Config.RGB_565, false);
            FaceDetector fd = new FaceDetector(work565.getWidth(), work565.getHeight(), 1);
            FaceDetector.Face[] faces = new FaceDetector.Face[1];
            int found = fd.findFaces(work565, faces);
            if (found >= 1 && faces[0] != null) {
                FaceDetector.Face f = faces[0];
                PointF mid = new PointF();
                f.getMidPoint(mid);
                float d = f.eyesDistance();
                float left = mid.x - 1.2f * d;
                float top = mid.y - 1.6f * d;
                float right = mid.x + 1.2f * d;
                float bottom = mid.y + 1.6f * d;
                int wl = work565.getWidth();
                int hl = work565.getHeight();
                int x = Math.max(0, Math.round(left));
                int y = Math.max(0, Math.round(top));
                int w = Math.min(wl - x, Math.round(right - left));
                int h = Math.min(hl - y, Math.round(bottom - top));
                if (w > 0 && h > 0) {
                    float sx = (float) src.getWidth() / (float) work565.getWidth();
                    float sy = (float) src.getHeight() / (float) work565.getHeight();
                    int ox = Math.max(0, Math.min(src.getWidth() - 1, Math.round(x * sx)));
                    int oy = Math.max(0, Math.min(src.getHeight() - 1, Math.round(y * sy)));
                    int ow = Math.max(1, Math.min(src.getWidth() - ox, Math.round(w * sx)));
                    int oh = Math.max(1, Math.min(src.getHeight() - oy, Math.round(h * sy)));
                    return Bitmap.createBitmap(src, ox, oy, ow, oh);
                }
            }
        } catch (Throwable ignored) {}
        return centerCrop(src);
    }
}