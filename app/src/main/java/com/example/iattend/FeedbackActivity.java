package com.example.iattend;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class FeedbackActivity extends AppCompatActivity {

    private AutoCompleteTextView actvType;
    private TextInputEditText etContent;
    private Button btnSubmit;
    private ProgressBar progressBar;

    private String[] feedbackTypes = {
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
        setupFeedbackTypes();
        setupClickListeners();
    }

    private void initViews() {
        actvType = findViewById(R.id.actvType);
        etContent = findViewById(R.id.etContent);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupFeedbackTypes() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                feedbackTypes
        );
        actvType.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitFeedback();
            }
        });
    }

    private void submitFeedback() {
        String type = actvType.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (type.isEmpty()) {
            showError(actvType, "Please select feedback type");
            return;
        }

        if (content.isEmpty()) {
            showError(etContent, "Please enter feedback content");
            return;
        }

        if (content.length() < 10) {
            showError(etContent, "Feedback content should be at least 10 characters");
            return;
        }

        // 显示加载
        showLoading(true);

        // 模拟提交到服务器
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000); // 模拟网络请求

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            showSuccessMessage();
                        }
                    });

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void showError(TextInputEditText editText, String message) {
        editText.setError(message);
        editText.requestFocus();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!show);
    }

    private void showSuccessMessage() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Success")
                .setMessage("Thank you for your feedback! We will review it soon.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .show();
    }
}