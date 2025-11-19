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

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvLogin;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        authService = new AuthService();
        setupClickListeners();
    }

    private void initViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
    }

    private void setupClickListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());

        tvLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void attemptRegister() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (validateInput(name, email, password)) {
            performRegister(name, email, password);
        }
    }

    private boolean validateInput(String name, String email, String password) {
        if (name.isEmpty()) {
            showToast("Please enter your name");
            return false;
        }

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

        if (password.length() < 6) {
            showToast("Password length should be at least 6 characters");
            return false;
        }

        return true;
    }

    private void performRegister(String name, String email, String password) {
        showLoading(true);

        CompletableFuture<AuthResult> registerFuture = authService.signUp(email, password, name);

        registerFuture.thenAccept(result -> {
            runOnUiThread(() -> {
                showLoading(false);

                if (result.isSuccess()) {
                    handleRegisterSuccess(result);
                } else {
                    handleRegisterFailure(result.getMessage());
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

    private void handleRegisterSuccess(AuthResult result) {
        showToast("Registered successfully! Please check your email to complete verification");

        // 注册成功后自动跳转到登录界面
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.putExtra("email", etEmail.getText().toString().trim());
        startActivity(intent);
        finish();
    }

    private void handleRegisterFailure(String errorMessage) {
        // 根据后端返回的错误信息进行匹配
        if (errorMessage.contains("该邮箱已被注册") || errorMessage.contains("User already registered")) {
            showToast("This email has already been registered");
        } else if (errorMessage.contains("邮箱格式不正确") || errorMessage.contains("Invalid email")) {
            showToast("Email format incorrect");
        } else if (errorMessage.contains("密码长度不足") || errorMessage.contains("Password should be at least")) {
            showToast("Password length should be at least 6 characters");
        } else {
            showToast(errorMessage);
        }
    }

    private void showLoading(boolean show) {
        findViewById(R.id.progressBar).setVisibility(show ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!show);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}