package com.example.iattend;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.google.gson.Gson;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class MapActivity extends AppCompatActivity implements AMapLocationListener, AMap.OnMapClickListener {

    private MapView mapView;
    private AMap aMap;
    private AMapLocationClient locationClient;
    private TextView tvTimer;
    private View btnSignIn;
    private CountDownTimer countDownTimer;
    private Marker currentLocationMarker;
    private double lat, lon, radius;
    private boolean isSign = false;
    private OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private static final int REQ_LOC = 2002;

    public static void start(Context context, double lat, double lon, double radius) {
        Intent intent = new Intent(context, MapActivity.class);
        intent.putExtra("lat", lat);
        intent.putExtra("lon", lon);
        intent.putExtra("radius", radius);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(s -> {
            Log.d("HTTP", s);
        });
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        setContentView(R.layout.activity_map);
        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);
        radius = getIntent().getDoubleExtra("radius", 0);
        initView();
        mapView.onCreate(savedInstanceState);
        initMap();
        if (hasLocationPermission()) {
            startLocation();
        } else {
            requestLocationPermission();
        }
        startCountDown();
    }

    private void initView() {
        mapView = findViewById(R.id.mapView);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvTimer = findViewById(R.id.tvTimer);
        btnSignIn.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        isSign = true;
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.startLocation();
        }
    }

    private void initMap() {
        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.setOnMapClickListener(this);
            // 设置地图属性
            aMap.getUiSettings().setZoomControlsEnabled(true);
            aMap.getUiSettings().setCompassEnabled(true);
            aMap.getUiSettings().setScaleControlsEnabled(true);
        }
    }

    private void startLocation() {
        try {
            locationClient = new AMapLocationClient(this);
            AMapLocationClientOption option = new AMapLocationClientOption();
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setOnceLocation(false);
            option.setInterval(5000);
            locationClient.setLocationOption(option);
            locationClient.setLocationListener(this);
            locationClient.startLocation();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOC) {
            boolean granted = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted) {
                startLocation();
            } else {
                Toast.makeText(this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCountDown() {
        countDownTimer = new CountDownTimer(261000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(millisUntilFinished / 1000 + "秒");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0秒");
                btnSignIn.setEnabled(false);
                btnSignIn.setBackgroundColor(Color.GRAY);
            }
        }.start();
    }

    @Override
    public void onLocationChanged(AMapLocation location) {
        if (location != null && location.getErrorCode() == 0) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            LatLng latLng = new LatLng(latitude, longitude);
            // 清除之前的标记
            if (currentLocationMarker != null) {
                currentLocationMarker.remove();
            }
            // 添加当前位置标记
            currentLocationMarker = aMap.addMarker(new MarkerOptions().position(latLng).title("当前位置")
                    .snippet("纬度:" + latitude + ", 经度:" + longitude).icon(BitmapDescriptorFactory
                            .defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            // 移动地图到当前位置
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
            if (isSign) {
                isSign = false;
                float distance = AMapUtils.calculateLineDistance(new LatLng(latitude, longitude), new LatLng(lat, lon));
                if (distance > radius) {
                    //超出范围
                    Toast.makeText(this, "超出位置，无法签到", Toast.LENGTH_SHORT).show();
                } else {
                    String user_id = getSharedPreferences("auth", MODE_PRIVATE).getString(
                            "user_id", "");
                    postCheckin(user_id);
                }
            }
        } else {
            Toast.makeText(this, "定位失败: " + location.getErrorInfo(), Toast.LENGTH_SHORT).show();
        }
    }


    private void postCheckin(String userId) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = "http://10.0.2.2:8080/api/checkin";
                MediaType json = MediaType.parse("application/json; charset=utf-8");
                String payload = "{\"userId\":\"" + userId + "\"}";
                RequestBody body = RequestBody.create(payload, json);
                Request request = new Request.Builder().url(url).post(body).build();
                try (Response resp = httpClient.newCall(request).execute()) {
                    String b = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        BaseResp r = gson.fromJson(b, BaseResp.class);
                        runOnUiThread(() -> Toast.makeText(this, r != null ? r.msg : "", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.check_in_report_failed), Toast.LENGTH_SHORT).show());
            }
        });
    }


    private static class BaseResp {
        int code;
        String msg;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        // 地图点击事件
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
        }
        mapView.onDestroy();
    }
}
