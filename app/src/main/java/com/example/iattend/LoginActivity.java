package com.example.iattend;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.iattend.backend.AuthService;
import com.example.iattend.domain.model.AuthResult;
import java.util.concurrent.CompletableFuture;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        authService = new AuthService();

        // 检查用户是否已登录
        checkLoginStatus();

        setupClickListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
    }

    private void checkLoginStatus() {
        if (authService.isUserLoggedIn()) {
            // 用户已登录，跳转到主界面
            navigateToMain();
        }
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (validateInput(email, password)) {
            performLogin(email, password);
        }
    }

    private boolean validateInput(String email, String password) {
        if (email.isEmpty()) {
            showToast("Please enter your email");
            return false;
        }

        if (password.isEmpty()) {
            showToast("Please enter your password");
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Email format incorrect");
            return false;
        }

        return true;
    }

    private void performLogin(String email, String password) {
        showLoading(true);

        CompletableFuture<AuthResult> loginFuture = authService.signIn(email, password);

        loginFuture.thenAccept(result -> {
            runOnUiThread(() -> {
                showLoading(false);

                if (result.isSuccess()) {
                    handleLoginSuccess(result);
                } else {
                    handleLoginFailure(result.getMessage());
                }
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                showLoading(false);
                showToast("Network connection failed, please check network settings");
            });
            return null;
        });
    }

    private void handleLoginSuccess(AuthResult result) {
        // 保存用户信息和token
        saveUserInfo(result.getUser(), result.getToken());
        showToast("Login successful");
        navigateToMain();
    }

    private void handleLoginFailure(String errorMessage) {
        // 根据后端返回的错误信息进行匹配
        if (errorMessage.contains("邮箱或密码错误") || errorMessage.contains("Invalid login credentials")) {
            showToast("Email or password input error");
        } else if (errorMessage.contains("该邮箱已被注册") || errorMessage.contains("User already registered")) {
            showToast("You haven't registered yet, please register an account first");
        } else if (errorMessage.contains("邮箱尚未验证") || errorMessage.contains("Email not confirmed")) {
            showToast("Your email has not been verified yet. Please check your email and complete the verification");
        } else {
            showToast(errorMessage);
        }
    }

    private void saveUserInfo(com.example.iattend.domain.model.User user, String token) {
        // 使用SharedPreferences保存用户信息和token
        getSharedPreferences("auth", MODE_PRIVATE)
                .edit()
                .putString("token", token)
                .putString("user_id", user.getId())
                .putString("user_name", user.getName())
                .putString("user_email", user.getEmail())
                .putString("user_class", user.getUserClass() != null ? user.getUserClass() : "")
                .putBoolean("is_logged_in", true)
                .apply();
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        findViewById(R.id.progressBar).setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}