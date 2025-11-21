package com.example.iattend;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.iattend.backend.AuthService;
import com.example.iattend.backend.UserService;
import com.example.iattend.domain.model.AuthResult;
import com.example.iattend.domain.model.User;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class CollectFaceActivity extends AppCompatActivity {

    private TextureView textureView;
    private Button btnTakePhoto, btnRetake, btnConfirm;
    private Camera camera;
    private Bitmap capturedBitmap;
    private AuthService authService;
    private UserService userService;

    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final String[] CAMERA_PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collect_face);

        initViews();
        authService = new AuthService();
        userService = new UserService();

        if (checkCameraPermission()) {
            setupCamera();
        } else {
            requestCameraPermission();
        }

        setupClickListeners();
    }

    private void initViews() {
        textureView = findViewById(R.id.textureView);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnRetake = findViewById(R.id.btnRetake);
        btnConfirm = findViewById(R.id.btnConfirm);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera();
            } else {
                showToast("Camera permission is required to take photos");
                finish();
            }
        }
    }

    private void setupCamera() {
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);

            camera.setPreviewTexture(textureView.getSurfaceTexture());
            camera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Failed to open camera");
        }
    }

    private void setupClickListeners() {
        btnTakePhoto.setOnClickListener(v -> takePhoto());
        btnRetake.setOnClickListener(v -> retakePhoto());
        btnConfirm.setOnClickListener(v -> confirmPhoto());
    }

    private void takePhoto() {
        if (camera != null) {
            camera.takePicture(null, null, (data, camera) -> {
                capturedBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                capturedBitmap = rotateBitmap(capturedBitmap, 90);

                runOnUiThread(this::showPhotoControls);
            });
        }
    }

    private void retakePhoto() {
        capturedBitmap = null;

        btnTakePhoto.setVisibility(View.VISIBLE);
        btnRetake.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);

        if (camera != null) {
            camera.startPreview();
        }
    }

    private void confirmPhoto() {
        if (capturedBitmap != null) {
            uploadImageToServer(capturedBitmap);
        }
    }

    private void uploadImageToServer(Bitmap bitmap) {
        showLoading(true);

        // 将 Bitmap 转换为 byte array
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        byte[] imageData = stream.toByteArray();

        // 生成文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "avatar_" + timeStamp + ".jpg";

        // 获取当前用户
        CompletableFuture<User> userFuture = authService.getCurrentUser();
        userFuture.thenAccept(user -> {
            if (user == null) {
                runOnUiThread(() -> {
                    showLoading(false);
                    showToast("User not logged in");
                });
                return;
            }

            // 上传头像
            CompletableFuture<String> uploadFuture = userService.uploadAvatar(user.getId(), fileName, imageData);
            uploadFuture.thenAccept(avatarUrl -> {
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    // 更新用户信息的头像URL
                    updateUserAvatar(user, avatarUrl);
                } else {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showToast("Failed to upload avatar");
                    });
                }
            }).exceptionally(throwable -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    showToast("Upload failed: " + throwable.getMessage());
                });
                return null;
            });

        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                showLoading(false);
                showToast("Failed to get user data");
            });
            return null;
        });
    }

    private void updateUserAvatar(User user, String avatarUrl) {
        User updatedUser = new User();
        updatedUser.setId(user.getId());
        updatedUser.setEmail(user.getEmail());
        updatedUser.setName(user.getName());
        updatedUser.setUserClass(user.getUserClass());
        updatedUser.setAvatarUrl(avatarUrl);

        CompletableFuture<AuthResult> updateFuture = authService.updateUser(updatedUser);
        updateFuture.thenAccept(result -> {
            runOnUiThread(() -> {
                showLoading(false);
                if (result.isSuccess()) {
                    showToast("Avatar updated successfully");
                    setResult(RESULT_OK);
                    finish();
                } else {
                    showToast("Failed to update avatar: " + result.getMessage());
                }
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                showLoading(false);
                showToast("Update failed: " + throwable.getMessage());
            });
            return null;
        });
    }

    private void showPhotoControls() {
        btnTakePhoto.setVisibility(View.GONE);
        btnRetake.setVisibility(View.VISIBLE);
        btnConfirm.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean show) {
        btnConfirm.setEnabled(!show);
        btnRetake.setEnabled(!show);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();
        mtx.postRotate(degree);

        if (Camera.CameraInfo.CAMERA_FACING_FRONT == 1) {
            mtx.postScale(-1, 1);
        }

        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null && checkCameraPermission()) {
            setupCamera();
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}