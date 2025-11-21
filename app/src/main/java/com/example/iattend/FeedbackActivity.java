package com.example.iattend;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.iattend.backend.AuthService;
import com.example.iattend.backend.FeedbackService;
import com.example.iattend.domain.model.User;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * 反馈页面
 * 用户提交反馈，支持文字和截图
 *
 * 工作流程：
 * 1. 选择反馈类型（下拉框）
 * 2. 输入反馈内容（最少10字符）
 * 3. 可选：选择截图（从相册）
 * 4. 提交反馈：
 *    - 无图片：直接 POST 到 feedbacks 表
 *    - 有图片：
 *      a) 上传到 Storage (feedback-images 桶)
 *      b) 获取 public URL
 *      c) INSERT 到 feedbacks 表（content + image_url）
 * 5. 成功提示并返回
 *
 * RLS：
 * - INSERT: auth.uid() = user_id
 * - SELECT: auth.uid() = user_id
 */
public class FeedbackActivity extends AppCompatActivity {

    private AutoCompleteTextView actvType;
    private TextInputEditText etContent;
    private Button btnSubmit;
    private Button btnSelectImage;
    private ImageView ivScreenshot;
    private ProgressBar progressBar;

    private AuthService authService;
    private byte[] selectedImageData = null;

    private static final int REQUEST_SELECT_IMAGE = 100;

    private static final String[] FEEDBACK_TYPES = {
        "Function Suggestion",
        "Bug Report",
        "UI/UX Improvement",
        "Performance Issue",
        "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        initViews();
        initServices();
        setupFeedbackTypes();
        setupClickListeners();
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        actvType = findViewById(R.id.actvType);
        etContent = findViewById(R.id.etContent);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        ivScreenshot = findViewById(R.id.ivScreenshot);
        progressBar = findViewById(R.id.progressBar);
    }

    /**
     * 初始化服务
     */
    private void initServices() {
        authService = new AuthService();
    }

    /**
     * 设置反馈类型下拉框
     */
    private void setupFeedbackTypes() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            FEEDBACK_TYPES
        );
        actvType.setAdapter(adapter);
    }

    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        btnSubmit.setOnClickListener(v -> submitFeedback());
        btnSelectImage.setOnClickListener(v -> selectImage());
    }

    /**
     * 从相册选择截图
     */
    private void selectImage() {
        Intent intent = new Intent(
            Intent.ACTION_PICK,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        );
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    /**
     * 处理图片选择结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SELECT_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri imageUri = data.getData();
                Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                    getContentResolver(),
                    imageUri
                );

                // 显示预览
                ivScreenshot.setVisibility(View.VISIBLE);
                ivScreenshot.setImageBitmap(bitmap);

                // 压缩并转换为 byte array（JPEG, 80%质量）
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                selectedImageData = stream.toByteArray();

                // 更改按钮文字
                btnSelectImage.setText("Change Image");

            } catch (Exception e) {
                e.printStackTrace();
                showToast("Failed to select image");
                selectedImageData = null;
            }
        }
    }

    /**
     * 提交反馈
     */
    private void submitFeedback() {
        String type = actvType.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (!validateInput(type, content)) {
            return;
        }

        // 获取当前用户信息
        authService.getCurrentUser().thenAccept(user -> {
            if (user == null) {
                runOnUiThread(() -> showToast("User not logged in"));
                return;
            }

            // 提交反馈
            submitToServer(user.getId(), type, content, selectedImageData);

        }).exceptionally(throwable -> {
            runOnUiThread(() -> showToast("Failed to get user data: " + throwable.getMessage()));
            return null;
        });
    }

    /**
     * 验证输入
     */
    private boolean validateInput(String type, String content) {
        if (type.isEmpty()) {
            actvType.setError("Please select feedback type");
            return false;
        }

        if (content.isEmpty()) {
            etContent.setError("Please enter feedback content");
            return false;
        }

        if (content.length() < 10) {
            etContent.setError("Feedback content should be at least 10 characters");
            return false;
        }

        return true;
    }

    /**
     * 提交到服务器
     */
    private void submitToServer(String userId, String type, String content, byte[] imageData) {
        showLoading(true);

        FeedbackService feedbackService = new FeedbackService();

        CompletableFuture<Boolean> submitFuture;
        if (imageData != null && imageData.length > 0) {
            submitFuture = feedbackService.submitFeedbackWithImage(userId, type, content, imageData);
        } else {
            submitFuture = feedbackService.submitFeedback(userId, type, content);
        }

        submitFuture.thenAccept(success -> {
            runOnUiThread(() -> {
                showLoading(false);
                if (success) {
                    showSuccessMessage();
                } else {
                    showToast("Failed to submit feedback");
                }
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                showLoading(false);
                showToast("Error: " + throwable.getMessage());
            });
            return null;
        });
    }

    /**
     * 显示加载状态
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!show);
        btnSelectImage.setEnabled(!show);
    }

    /**
     * 显示成功消息
     */
    private void showSuccessMessage() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Success")
            .setMessage("Thank you for your feedback! We will review it soon.")
            .setPositiveButton("OK", (dialog, which) -> finish())
            .show();
    }

    /**
     * 显示 Toast 提示
     */
    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}
