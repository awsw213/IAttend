package com.example.iattend;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Surface;
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
            android.Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collect_face);

        // 首先检查设备是否有相机硬件
        if (!checkCameraHardware()) {
            showToast("Device has no camera or camera is not accessible");
            finish();
            return;
        }

        initViews();
        authService = new AuthService();
        userService = new UserService();

        if (checkCameraPermission()) {
            // 相机权限已授予，等待 SurfaceTexture 就绪后 setupCamera() 会自动调用
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

        // 注册 SurfaceTexture 监听器
        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    // SurfaceTexture 生命周期监听器
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // SurfaceTexture 已就绪，此时可以安全打开相机
            if (checkCameraPermission()) {
                setupCamera();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            releaseCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

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
                // 权限已授予，检查设备是否支持相机
                if (checkCameraHardware() && textureView.isAvailable()) {
                    setupCamera();
                } else if (!textureView.isAvailable()) {
                    showToast("Waiting for camera preview...");
                } else {
                    // 如果 checkCameraHardware() 返回 false，showToast 已经在里面调用过了
                    finish();
                }
            } else {
                showToast("Camera permission is required to take photos");
                finish();
            }
        }
    }

    // 检查设备是否有相机硬件
    private boolean checkCameraHardware() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return true;
        } else if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        }
        showToast("Device has no camera");
        return false;
    }

    private void setupCamera() {
        try {
            if (!checkCameraHardware()) {
                finish();
                return;
            }

            // 1. 检查并获取相机ID
            int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

            // 验证前置摄像头是否存在
            boolean hasFrontCamera = false;
            int numberOfCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    cameraId = i;
                    hasFrontCamera = true;
                    break;
                }
            }

            if (!hasFrontCamera && numberOfCameras > 0) {
                cameraId = 0; // 使用默认摄像头
                Camera.getCameraInfo(cameraId, cameraInfo);
            }

            // 2. 安全打开相机
            camera = Camera.open(cameraId);
            if (camera == null) {
                showToast("Failed to open camera: camera is null");
                finish();
                return;
            }

            // 3. 配置参数（增加兼容性检查）
            Camera.Parameters parameters = camera.getParameters();

            // 安全设置对焦模式
            if (parameters.getSupportedFocusModes() != null) {
                if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }

            camera.setParameters(parameters);

            // 4. 设置预览旋转（关键修复）
            setCameraDisplayOrientation(cameraId, cameraInfo);

            // 5. 设置预览（确保 SurfaceTexture 可用）
            if (textureView.getSurfaceTexture() != null) {
                camera.setPreviewTexture(textureView.getSurfaceTexture());
                camera.startPreview();
            } else {
                showToast("SurfaceTexture not ready");
                finish();
            }

        } catch (Exception e) {
            e.printStackTrace();
            showToast("Camera error: " + e.getMessage());
            finish();
        }
    }

    /**
     * 设置相机预览旋转方向
     * 确保预览画面在竖屏模式下正确显示
     */
    private void setCameraDisplayOrientation(int cameraId, Camera.CameraInfo cameraInfo) {
        if (camera == null) return;

        // 获取设备当前旋转角度
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        // 计算预览方向
        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            // 前置摄像头
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360; // 补偿前置摄像头的镜像
        } else {
            // 后置摄像头
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }

        // 设置预览旋转
        camera.setDisplayOrientation(result);
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
                capturedBitmap = rotateCapturedBitmap(capturedBitmap);

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

    /**
     * 智能旋转拍摄的图片
     * 根据当前使用的摄像头方向动态调整
     */
    private Bitmap rotateCapturedBitmap(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();

        // 获取设备当前旋转角度
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        // 获取当前相机信息
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();

        // 查找当前打开的相机
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                break;
            }
        }

        if (cameraId == -1 && numberOfCameras > 0) {
            cameraId = 0;
            Camera.getCameraInfo(cameraId, cameraInfo);
        }

        // 计算旋转角度
        int rotateDegree;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotateDegree = (cameraInfo.orientation + degrees) % 360;
            rotateDegree = (360 - rotateDegree) % 360; // 镜像补偿
        } else {
            rotateDegree = (cameraInfo.orientation - degrees + 360) % 360;
        }

        // 应用旋转
        mtx.postRotate(rotateDegree);

        // 前置摄像头需要镜像处理
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
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
        // 检查 SurfaceTexture 是否已可用
        if (camera == null && checkCameraPermission() && textureView.isAvailable()) {
            setupCamera();
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            camera = null;
        }
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}