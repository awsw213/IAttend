package com.example.iattend;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.iattend.backend.AuthService;
import com.example.iattend.domain.model.User;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 用户中心页面
 * 显示和编辑用户个人信息
 *
 * 功能：
 * 1. 显示用户头像、姓名、班级、邮箱
 * 2. 编辑姓名和班级
 * 3. 重新采集头像
 * 4. 提交反馈
 * 5. 本地缓存 + 服务器同步
 */
public class UserCenterActivity extends AppCompatActivity {

    private ImageView ivAvatar;
    private TextView tvName, tvClass, tvEmail;
    private ProgressBar progressBar;
    private View userInfoContainer;
    private AuthService authService;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int MAX_RETRY_COUNT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_center);

        initViews();
        initServices();
        loadUserData();
        setupClickListeners();
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        ivAvatar = findViewById(R.id.ivAvatar);
        tvName = findViewById(R.id.tvName);
        tvClass = findViewById(R.id.tvClass);
        tvEmail = findViewById(R.id.tvEmail);
        progressBar = findViewById(R.id.progressBar);
        userInfoContainer = findViewById(R.id.userInfoContainer);
    }

    /**
     * 初始化服务
     */
    private void initServices() {
        authService = new AuthService();
    }

    /**
     * 加载用户数据
     * 流程：本地缓存 → 服务器（异步）
     */
    private void loadUserData() {
        // 1. 先显示本地缓存
        loadUserDataFromLocal();

        // 2. 异步获取最新数据
        loadUserDataFromServer();
    }

    /**
     * 从本地 SharedPreferences 加载用户数据
     */
    private void loadUserDataFromLocal() {
        String userName = getSharedPreferences("auth", MODE_PRIVATE)
                .getString("user_name", "User Name");
        String userEmail = getSharedPreferences("auth", MODE_PRIVATE)
                .getString("user_email", "user@example.com");
        String userClass = getSharedPreferences("auth", MODE_PRIVATE)
                .getString("user_class", "Class A");
        String avatarUrl = getSharedPreferences("auth", MODE_PRIVATE)
                .getString("user_avatar", "");

        tvName.setText(userName);
        tvClass.setText(userClass);
        tvEmail.setText(userEmail);

        if (!avatarUrl.isEmpty()) {
            loadAvatarFromUrl(avatarUrl);
        }
    }

    /**
     * 从服务器加载用户数据（带重试）
     */
    private void loadUserDataFromServer() {
        showLoading(true);

        executeWithRetry(() -> authService.getCurrentUser(), 0)
            .thenAccept(user -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    if (user != null) {
                        updateUIWithUserData(user);
                        saveUserInfoToPrefs(user);
                    } else {
                        showToast("Failed to load user data");
                    }
                });
            })
            .exceptionally(throwable -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    showToast("Network error: " + throwable.getMessage());
                });
                return null;
            });
    }

    /**
     * 更新 UI 显示用户数据
     */
    private void updateUIWithUserData(User user) {
        tvName.setText(user.getName() != null ? user.getName() : "User Name");
        tvClass.setText(user.getUserClass() != null ? user.getUserClass() : "Class A");
        tvEmail.setText(user.getEmail() != null ? user.getEmail() : "user@example.com");

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            loadAvatarFromUrl(user.getAvatarUrl());
        }
    }

    /**
     * 使用 Glide 加载头像
     */
    private void loadAvatarFromUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return;
        }

        try {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .circleCrop()
                .into(ivAvatar);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存用户信息到 SharedPreferences
     */
    private void saveUserInfoToPrefs(User user) {
        getSharedPreferences("auth", MODE_PRIVATE)
            .edit()
            .putString("user_id", user.getId())
            .putString("user_name", user.getName())
            .putString("user_email", user.getEmail())
            .putString("user_class", user.getUserClass())
            .putString("user_avatar", user.getAvatarUrl())
            .apply();
    }

    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        ivAvatar.setOnClickListener(v -> showRecollectDialog());

        tvName.setOnClickListener(v ->
            showEditDialog("Name", "Enter new name", tvName.getText().toString(), "name"));

        findViewById(R.id.layoutClass).setOnClickListener(v ->
            showEditDialog("Class", "Enter new class", tvClass.getText().toString(), "class"));

        findViewById(R.id.layoutFeedback).setOnClickListener(v ->
            startActivity(new Intent(UserCenterActivity.this, FeedbackActivity.class)));

        setupNavigationBar();
    }

    private void setupNavigationBar() {
        android.widget.TextView tvHome = findViewById(R.id.tvHome);
        android.widget.TextView tvHistory = findViewById(R.id.tvHistory);
        android.widget.TextView tvPersonal = findViewById(R.id.tvPersonal);

        // 设置点击监听器
        findViewById(R.id.navHome).setOnClickListener(v -> {
            // 如果当前不在首页，跳转
            if (this instanceof UserCenterActivity) {
                Intent intent = new Intent(this, MainActivity.class);
                reOrderTasks(intent);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });

        findViewById(R.id.navHistory).setOnClickListener(v -> {
            // 如果当前不在历史页面，跳转
            if (this instanceof UserCenterActivity) {
                // 直接跳转到MonitorActivity，暂时不支持传递session数据
                // 如果需要支持，需要修改MainActivity启动流程
                Intent intent = new Intent(this, MonitorActivity.class);
                reOrderTasks(intent);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });

        findViewById(R.id.navPersonalCentre).setOnClickListener(v -> {
            // 当前已经在个人中心页面，不做任何操作
            // 这里可以添加刷新逻辑
        });

        // 高亮当前选中的tab（UserCenterActivity对应PERSONAL）
        tvPersonal.setSelected(true);
        tvHome.setSelected(false);
        tvHistory.setSelected(false);
    }

    private void reOrderTasks(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    /**
     * 显示重新采集人脸对话框
     */
    private void showRecollectDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Re-collect Facial Information")
            .setMessage("Do you want to re-collect your facial information?\n\nThis will update your avatar.")
            .setPositiveButton("Confirm", (dialog, which) -> startCollectFaceActivity())
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * 启动人脸采集页面
     */
    private void startCollectFaceActivity() {
        Intent intent = new Intent(this, CollectFaceActivity.class);
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
    }

    /**
     * 显示编辑对话框
     */
    private void showEditDialog(String title, String hint, String currentValue, String field) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        EditText etEdit = dialogView.findViewById(R.id.etEdit);
        etEdit.setHint(hint);
        etEdit.setText(currentValue);
        etEdit.requestFocus();

        new AlertDialog.Builder(this)
            .setTitle("Edit " + title)
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                String newValue = etEdit.getText().toString().trim();
                if (!newValue.isEmpty() && !newValue.equals(currentValue)) {
                    updateUserInfo(field, newValue);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * 更新用户信息（乐观更新 + 同步服务器）
     */
    private void updateUserInfo(String field, String newValue) {
        // 1. 乐观更新 UI
        runOnUiThread(() -> {
            if ("name".equals(field)) {
                tvName.setText(newValue);
            } else {
                tvClass.setText(newValue);
            }
        });

        // 2. 立即更新本地缓存
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        prefs.edit()
            .putString("user_" + field, newValue)
            .apply();

        // 3. 获取当前用户并更新到服务器
        authService.getCurrentUser().thenAccept(currentUser -> {
            if (currentUser == null) {
                runOnUiThread(() -> showToast("User not logged in"));
                return;
            }

            User updatedUser = createUpdatedUser(currentUser, field, newValue);

            authService.updateUser(updatedUser).thenAccept(result -> {
                runOnUiThread(() -> {
                    if (result.isSuccess()) {
                        showToast("Update successful");
                        saveUserInfoToPrefs(result.getUser());
                    } else {
                        showToast("Update failed: " + result.getMessage());
                        loadUserDataFromLocal(); // 回滚
                    }
                });
            }).exceptionally(throwable -> {
                runOnUiThread(() -> {
                    showToast("Network error: " + throwable.getMessage());
                    loadUserDataFromLocal(); // 回滚
                });
                return null;
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> showToast("Failed to get user data"));
            return null;
        });
    }

    /**
     * 创建更新后的 User 对象
     */
    private User createUpdatedUser(User currentUser, String field, String newValue) {
        User updatedUser = new User();
        updatedUser.setId(currentUser.getId());
        updatedUser.setEmail(currentUser.getEmail());
        updatedUser.setAvatarUrl(currentUser.getAvatarUrl());

        if ("name".equals(field)) {
            updatedUser.setName(newValue);
            updatedUser.setUserClass(currentUser.getUserClass());
        } else {
            updatedUser.setName(currentUser.getName());
            updatedUser.setUserClass(newValue);
        }

        return updatedUser;
    }

    /**
     * 处理 Activity 返回结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // 人脸采集返回，重新加载数据
            loadUserDataFromServer();
            showToast("Avatar updated successfully");
        }
    }

    /**
     * 显示加载状态
     */
    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            if (show) {
                progressBar.setVisibility(View.VISIBLE);
                userInfoContainer.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.GONE);
                userInfoContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * 带重试机制执行异步操作（网络容错）
     */
    private <T> CompletableFuture<T> executeWithRetry(
            Supplier<CompletableFuture<T>> operation, int retryCount) {

        CompletableFuture<T> future = operation.get();

        return future.exceptionally(throwable -> {
            if (retryCount < MAX_RETRY_COUNT) {
                // 等待2秒后重试
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return executeWithRetry(operation, retryCount + 1).join();
            }
            throw new RuntimeException("Max retry count reached", throwable);
        });
    }

    /**
     * 显示 Toast 提示
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
