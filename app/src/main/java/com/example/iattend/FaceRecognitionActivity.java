package com.example.iattend;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.face.FaceRecognition;
import com.example.face.TfLiteFaceEmbedder;
import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FaceRecognitionActivity extends AppCompatActivity {

    private static final int REQ_CAPTURE_REF = 3001;
    private static final int REQ_CAPTURE_PROBE = 3002;

    private ImageView ivPreview;
    private Button btnCaptureRef;
    private Button btnVerify;
    private TextView tvSimilarity;
    private TextView tvStatus;

    private Bitmap refBitmap;
    private String refAssetName;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_recognition);

        ivPreview = findViewById(R.id.ivPreview);
        btnCaptureRef = findViewById(R.id.btnCaptureRef);
        btnVerify = findViewById(R.id.btnVerify);
        tvSimilarity = findViewById(R.id.tvSimilarity);
        tvStatus = findViewById(R.id.tvStatus);

        refAssetName = getIntent().getStringExtra("refAssetName");
        if (refAssetName == null || refAssetName.isEmpty()) refAssetName = "IMG_7308.JPG";
        loadRefFromAssets();
        btnCaptureRef.setOnClickListener(v -> loadRefFromAssets());
        btnVerify.setOnClickListener(v -> {
            if (ensureCameraPermission()) capture(REQ_CAPTURE_PROBE);
        });
    }

    private void capture(int req) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, req);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        Bitmap bmp = (Bitmap) data.getExtras().get("data");
        if (bmp == null) return;
        ivPreview.setImageBitmap(bmp);
        if (requestCode == REQ_CAPTURE_PROBE) {
            if (refBitmap == null) {
                Toast.makeText(this, getString(R.string.please_enter_code), Toast.LENGTH_SHORT).show();
                return;
            }
            float sim = computeSimilarity(refBitmap, bmp);
            tvSimilarity.setText("相似度: " + String.format(java.util.Locale.getDefault(), "%.3f", sim));
            if (sim >= 0.7f) {
                tvStatus.setText("状态: 人脸通过，正在上报...");
                doCheckin();
            } else {
                tvStatus.setText("状态: 人脸未通过");
            }
        }
    }

    private float computeSimilarity(Bitmap ref, Bitmap probe) {
        try {
            String asset = getIntent().getStringExtra("modelAssetName");
            if (asset == null || asset.isEmpty()) asset = "mobile_face_net.tflite";
            TfLiteFaceEmbedder emb = new TfLiteFaceEmbedder(this, asset);
            FaceRecognition fr = new FaceRecognition(emb).setAlpha(13.9f).setCenter(0.30f);
            float sim = fr.computeSimilarity(ref, probe);
            return sim;
        } catch (Throwable t) {
            Toast.makeText(this, "模型加载失败，请检查 assets", Toast.LENGTH_SHORT).show();
            return 0f;
        }
    }

    private boolean ensureCameraPermission() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return true;
        androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 5001);
        return false;
    }

    private void loadRefFromAssets() {
        try {
            java.io.InputStream is = getAssets().open(refAssetName);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            refBitmap = bmp;
            ivPreview.setImageBitmap(bmp);
            Toast.makeText(this, "已加载参考图片", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "参考图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void doCheckin() {
        String userId = getSharedPreferences("auth", MODE_PRIVATE).getString("user_id", "");
        new Thread(() -> {
            try {
                String url = "http://10.0.2.2:8080/api/checkin";
                MediaType json = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"userId\":\"" + userId + "\"}";
                RequestBody body = RequestBody.create(payload, json);
                Request request = new Request.Builder().url(url).post(body).build();
                try (Response resp = httpClient.newCall(request).execute()) {
                    boolean ok = resp.isSuccessful();
                    runOnUiThread(() -> {
                        if (ok) {
                            tvStatus.setText("状态: 上报成功");
                            Toast.makeText(this, getString(R.string.check_in_success), Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            tvStatus.setText("状态: 上报失败");
                            Toast.makeText(this, getString(R.string.check_in_report_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("状态: 上报失败");
                    Toast.makeText(this, getString(R.string.check_in_report_failed), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}