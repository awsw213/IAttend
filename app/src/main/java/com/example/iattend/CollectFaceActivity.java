package com.example.iattend;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CollectFaceActivity extends AppCompatActivity {

    private TextureView textureView;
    private Button btnTakePhoto, btnRetake, btnConfirm;
    private Camera camera;
    private Bitmap capturedBitmap;
    private boolean isPhotoTaken = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collect_face);

        initViews();
        setupCamera();
        setupClickListeners();
    }

    private void initViews() {
        textureView = findViewById(R.id.textureView);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnRetake = findViewById(R.id.btnRetake);
        btnConfirm = findViewById(R.id.btnConfirm);
    }

    private void setupCamera() {
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            Camera.Parameters parameters = camera.getParameters();
            // 设置相机参数
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);

            // 设置预览显示
            camera.setPreviewTexture(textureView.getSurfaceTexture());
            camera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupClickListeners() {
        btnTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });

        btnRetake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retakePhoto();
            }
        });

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmPhoto();
            }
        });
    }

    private void takePhoto() {
        if (camera != null) {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    // 处理拍摄的照片
                    capturedBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                    // 旋转图片（前置摄像头需要镜像处理）
                    capturedBitmap = rotateBitmap(capturedBitmap, 90);

                    // 更新UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showPhotoControls();
                        }
                    });
                }
            });
        }
    }

    private void retakePhoto() {
        isPhotoTaken = false;
        capturedBitmap = null;

        btnTakePhoto.setVisibility(View.VISIBLE);
        btnRetake.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);

        // 重新开始预览
        if (camera != null) {
            camera.startPreview();
        }
    }

    private void confirmPhoto() {
        if (capturedBitmap != null) {
            // 保存图片到本地
            String imagePath = saveImageToStorage(capturedBitmap);

            // 更新用户头像路径到数据库/SharedPreferences
            UserPreferences.setAvatarPath(this, imagePath);

            // 返回结果
            setResult(RESULT_OK);
            finish();
        }
    }

    private void showPhotoControls() {
        isPhotoTaken = true;

        btnTakePhoto.setVisibility(View.GONE);
        btnRetake.setVisibility(View.VISIBLE);
        btnConfirm.setVisibility(View.VISIBLE);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();
        mtx.postRotate(degree);

        // 前置摄像头需要镜像
        if (Camera.CameraInfo.CAMERA_FACING_FRONT == 1) {
            mtx.postScale(-1, 1);
        }

        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    private String saveImageToStorage(Bitmap bitmap) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "FACE_" + timeStamp + ".jpg";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = new File(storageDir, imageFileName);

        try {
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            return imageFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null) {
            setupCamera();
        }
    }
}