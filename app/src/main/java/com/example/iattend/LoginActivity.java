package com.example.iattend;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.iattend.backend.AuthService;
import com.example.iattend.data.remote.SupabaseClient;
import com.example.iattend.domain.model.AuthResult;
import com.example.iattend.domain.model.User;
import java.util.concurrent.CompletableFuture;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private CheckBox cbRemember;
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
        cbRemember = findViewById(R.id.cbRemember);

        String savedEmail = getSharedPreferences("auth", MODE_PRIVATE).getString("remember_email", "");
        String savedPassword = getSharedPreferences("auth", MODE_PRIVATE).getString("remember_password_value", "");
        boolean remember = getSharedPreferences("auth", MODE_PRIVATE).getBoolean("remember_password", false);
        if (remember) {
            etEmail.setText(savedEmail);
            etPassword.setText(savedPassword);
            cbRemember.setChecked(true);
        }
        String intentEmail = getIntent().getStringExtra("email");
        if (intentEmail != null && !intentEmail.isEmpty()) {
            etEmail.setText(intentEmail);
        }
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
            showToast(getString(R.string.enter_email));
            return false;
        }

        if (password.isEmpty()) {
            showToast(getString(R.string.enter_password));
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast(getString(R.string.email_format_incorrect));
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
                    handleLoginFailure(email, result.getMessage());
                }
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                showLoading(false);
                showToast(getString(R.string.network_failed));
            });
            return null;
        });
    }

    private void handleLoginSuccess(AuthResult result) {
        saveUserInfo(result.getUser(), result.getToken());
        if (cbRemember != null && cbRemember.isChecked()) {
            getSharedPreferences("auth", MODE_PRIVATE)
                    .edit()
                    .putBoolean("remember_password", true)
                    .putString("remember_email", etEmail.getText().toString().trim())
                    .putString("remember_password_value", etPassword.getText().toString().trim())
                    .apply();
        } else {
            getSharedPreferences("auth", MODE_PRIVATE)
                    .edit()
                    .putBoolean("remember_password", false)
                    .remove("remember_email")
                    .remove("remember_password_value")
                    .apply();
        }
        String pendingName = getSharedPreferences("auth", MODE_PRIVATE).getString("pending_profile_name", "");
        String pendingEmail = getSharedPreferences("auth", MODE_PRIVATE).getString("pending_profile_email", "");
        User u = new User();
        u.setId(result.getUser().getId());
        u.setName(result.getUser().getName() != null && !result.getUser().getName().isEmpty() ? result.getUser().getName() : pendingName);
        u.setEmail(result.getUser().getEmail() != null && !result.getUser().getEmail().isEmpty() ? result.getUser().getEmail() : pendingEmail);
        authService.updateUser(u)
                .thenAccept(r -> getSharedPreferences("auth", MODE_PRIVATE).edit().remove("pending_profile_name").remove("pending_profile_email").apply())
                .exceptionally(t -> null);
        showToast(getString(R.string.login_success));
        navigateToMain();
    }

    private void handleLoginFailure(String email, String errorMessage) {
        if (errorMessage == null) {
            showToast(getString(R.string.email_password_error));
            return;
        }
        if (errorMessage.contains("邮箱尚未验证") || errorMessage.contains("Email not confirmed")) {
            SupabaseClient.getInstance().resendVerificationEmail(email)
                    .thenAccept(success -> runOnUiThread(() -> {
                        if (success) {
                            showToast(getString(R.string.verification_email_resent));
                        } else {
                            showToast(getString(R.string.email_not_verified));
                        }
                    }));
            return;
        }
        if (errorMessage.contains("邮箱或密码错误") || errorMessage.contains("Invalid login credentials")) {
            SupabaseClient.getInstance().isEmailRegistered(email)
                    .thenAccept(registered -> runOnUiThread(() -> {
                        if (registered) {
                            showToast(getString(R.string.password_error));
                        } else {
                            showToast(getString(R.string.account_not_registered));
                        }
                    }))
                    .exceptionally(t -> {
                        runOnUiThread(() -> showToast(getString(R.string.email_password_error)));
                        return null;
                    });
            return;
        }
        if (errorMessage.contains("User already registered")) {
            showToast(getString(R.string.account_not_registered));
            return;
        }
        showToast(errorMessage);
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