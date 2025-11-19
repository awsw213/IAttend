package com.example.iattend;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class UserCenterActivity extends AppCompatActivity {

    private ImageView ivAvatar;
    private TextView tvName, tvClass, tvEmail;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_center);

        initViews();
        loadUserData();
        setupClickListeners();
    }

    private void initViews() {
        ivAvatar = findViewById(R.id.ivAvatar);
        tvName = findViewById(R.id.tvName);
        tvClass = findViewById(R.id.tvClass);
        tvEmail = findViewById(R.id.tvEmail);
    }

    private void loadUserData() {
        // 从 SharedPreferences 或数据库加载用户数据
        String userName = UserPreferences.getUserName(this);
        String userClass = UserPreferences.getUserClass(this);
        String userEmail = UserPreferences.getUserEmail(this);
        String avatarPath = UserPreferences.getAvatarPath(this);

        tvName.setText(userName);
        tvClass.setText(userClass);
        tvEmail.setText(userEmail);

        // 加载头像
        if (avatarPath != null && !avatarPath.isEmpty()) {
            loadAvatar(avatarPath);
        }
    }

    private void setupClickListeners() {
        // 头像点击 - 重新采集人脸信息
        ivAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRecollectDialog();
            }
        });

        // 姓名编辑
        findViewById(R.id.tvName).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditNameDialog();
            }
        });

        // 班级编辑
        findViewById(R.id.layoutClass).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditClassDialog();
            }
        });

        // 反馈页面
        findViewById(R.id.layoutFeedback).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(UserCenterActivity.this, FeedbackActivity.class));
            }
        });
    }

    private void showRecollectDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Re-collect Facial Information")
                .setMessage("Do you want to re-collect your facial information?")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startCollectFaceActivity();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startCollectFaceActivity() {
        Intent intent = new Intent(this, CollectFaceActivity.class);
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
    }

    private void showEditNameDialog() {
        // 实现编辑姓名的对话框
        new AlertDialog.Builder(this)
                .setTitle("Edit Name")
                .setView(R.layout.dialog_edit_text)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 保存逻辑
                        String newName = ""; // 从输入框获取
                        tvName.setText(newName);
                        UserPreferences.setUserName(UserCenterActivity.this, newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditClassDialog() {
        // 实现编辑班级的对话框
        new AlertDialog.Builder(this)
                .setTitle("Edit Class")
                .setView(R.layout.dialog_edit_text)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 保存逻辑
                        String newClass = ""; // 从输入框获取
                        tvClass.setText(newClass);
                        UserPreferences.setUserClass(UserCenterActivity.this, newClass);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadAvatar(String path) {
        File imgFile = new File(path);
        if (imgFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            ivAvatar.setImageBitmap(bitmap);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // 重新加载头像
            loadUserData();
        }
    }
}