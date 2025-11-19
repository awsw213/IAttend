package com.example.iattend;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 显示欢迎信息
        //TextView tvWelcome = findViewById(R.id.tvWelcome);
        //tvWelcome.setText("Welcome to IAttend!");
    }
}