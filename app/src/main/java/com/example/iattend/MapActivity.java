package com.example.iattend;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class MapActivity extends AppCompatActivity {

    private View mapView;
    private Object aMap;
    private Object locationClient;
    private TextView tvTimer;
    private View btnSignIn;
    private CountDownTimer countDownTimer;
    private Object currentLocationMarker;
    private double lat, lon, radius;
    private boolean isSign = false;
    private OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private static final int REQ_LOC = 2002;
    private boolean amapAvailable;

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

        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);
        radius = getIntent().getDoubleExtra("radius", 0);
        amapAvailable = isClassPresent("com.amap.api.maps.MapView");
        if (amapAvailable) {
            try {
                Class<?> mi = Class.forName("com.amap.api.maps.MapsInitializer");
                Method show = mi.getMethod("updatePrivacyShow", android.content.Context.class, boolean.class, boolean.class);
                Method agree = mi.getMethod("updatePrivacyAgree", android.content.Context.class, boolean.class);
                show.invoke(null, this, true, true);
                agree.invoke(null, this, true);
            } catch (Exception ignored) {}
        }
        if (amapAvailable) {
            setContentView(R.layout.activity_map);
            initView();
            try {
                Method mOnCreate = mapView.getClass().getMethod("onCreate", Bundle.class);
                mOnCreate.invoke(mapView, savedInstanceState);
            } catch (Exception ignored) {}
            initMap();
            if (hasLocationPermission()) {
                startLocation();
            } else {
                requestLocationPermission();
            }
            startCountDown();
        } else {
            RelativeLayout root = new RelativeLayout(this);
            root.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            TextView info = new TextView(this);
            info.setText("地图SDK缺失，无法显示地图与定位");
            info.setTextSize(16f);
            RelativeLayout.LayoutParams ip = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            ip.addRule(RelativeLayout.CENTER_IN_PARENT);
            root.addView(info, ip);
            Button fallbackBtn = new Button(this);
            fallbackBtn.setText("签到");
            fallbackBtn.setEnabled(false);
            RelativeLayout.LayoutParams bp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            bp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            bp.addRule(RelativeLayout.CENTER_HORIZONTAL);
            bp.bottomMargin = 48;
            root.addView(fallbackBtn, bp);
            tvTimer = new TextView(this);
            RelativeLayout.LayoutParams tp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            tp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            tp.addRule(RelativeLayout.CENTER_HORIZONTAL);
            tp.topMargin = 48;
            root.addView(tvTimer, tp);
            setContentView(root);
            startCountDown();
        }
    }

    private void initView() {
        mapView = findViewById(R.id.mapView);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvTimer = findViewById(R.id.tvTimer);
        btnSignIn.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        isSign = true;
        if (locationClient != null && amapAvailable) {
            try {
                Method stop = locationClient.getClass().getMethod("stopLocation");
                Method start = locationClient.getClass().getMethod("startLocation");
                stop.invoke(locationClient);
                start.invoke(locationClient);
            } catch (Exception ignored) {}
        } else if (!amapAvailable) {
            Toast.makeText(this, "缺少地图定位组件", Toast.LENGTH_SHORT).show();
        }
    }

    private void initMap() {
        if (!amapAvailable) return;
        try {
            Method getMap = mapView.getClass().getMethod("getMap");
            aMap = getMap.invoke(mapView);
        } catch (Exception ignored) {}
    }

    private void startLocation() {
        if (!amapAvailable) return;
        try {
            Class<?> clientCls = Class.forName("com.amap.api.location.AMapLocationClient");
            Constructor<?> cons = clientCls.getConstructor(Context.class);
            locationClient = cons.newInstance(this);
            Class<?> optionCls = Class.forName("com.amap.api.location.AMapLocationClientOption");
            Object option = optionCls.getConstructor().newInstance();
            Class<?> modeCls = Class.forName("com.amap.api.location.AMapLocationClientOption$AMapLocationMode");
            Field high = modeCls.getField("Hight_Accuracy");
            Object mode = high.get(null);
            Method setMode = optionCls.getMethod("setLocationMode", modeCls);
            setMode.invoke(option, mode);
            optionCls.getMethod("setOnceLocation", boolean.class).invoke(option, false);
            optionCls.getMethod("setInterval", long.class).invoke(option, 5000L);
            clientCls.getMethod("setLocationOption", optionCls).invoke(locationClient, option);
            Class<?> listenerCls = Class.forName("com.amap.api.location.AMapLocationListener");
            Object listener = Proxy.newProxyInstance(getClassLoader(), new Class[]{listenerCls}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if (method.getName().equals("onLocationChanged") && args != null && args.length > 0) {
                        Object loc = args[0];
                        try {
                            Method getErrorCode = loc.getClass().getMethod("getErrorCode");
                            int ec = (int) getErrorCode.invoke(loc);
                            if (ec == 0) {
                                double latitude = (double) loc.getClass().getMethod("getLatitude").invoke(loc);
                                double longitude = (double) loc.getClass().getMethod("getLongitude").invoke(loc);
                                Class<?> latLngCls = Class.forName("com.amap.api.maps.model.LatLng");
                                Object latLng = latLngCls.getConstructor(double.class, double.class).newInstance(latitude, longitude);
                                if (currentLocationMarker != null) {
                                    try {
                                        currentLocationMarker.getClass().getMethod("remove").invoke(currentLocationMarker);
                                    } catch (Exception ignored) {}
                                }
                                Class<?> moCls = Class.forName("com.amap.api.maps.model.MarkerOptions");
                                Object mo = moCls.getConstructor().newInstance();
                                mo = moCls.getMethod("position", latLngCls).invoke(mo, latLng);
                                mo = moCls.getMethod("title", String.class).invoke(mo, "当前位置");
                                mo = moCls.getMethod("snippet", String.class).invoke(mo, "纬度:" + latitude + ", 经度:" + longitude);
                                Class<?> bdfCls = Class.forName("com.amap.api.maps.model.BitmapDescriptorFactory");
                                Object icon = bdfCls.getMethod("defaultMarker", float.class).invoke(null, 240f);
                                mo = moCls.getMethod("icon", Class.forName("com.amap.api.maps.model.BitmapDescriptor")).invoke(mo, icon);
                                currentLocationMarker = aMap.getClass().getMethod("addMarker", moCls).invoke(aMap, mo);
                                Class<?> cufCls = Class.forName("com.amap.api.maps.CameraUpdateFactory");
                                Object cu = cufCls.getMethod("newLatLngZoom", latLngCls, float.class).invoke(null, latLng, 16f);
                                aMap.getClass().getMethod("moveCamera", Class.forName("com.amap.api.maps.CameraUpdate")).invoke(aMap, cu);
                                if (isSign) {
                                    isSign = false;
                                    Object target = latLngCls.getConstructor(double.class, double.class).newInstance(lat, lon);
                                    Class<?> amapUtils = Class.forName("com.amap.api.maps.AMapUtils");
                                    float distance = (float) amapUtils.getMethod("calculateLineDistance", latLngCls, latLngCls).invoke(null, latLng, target);
                                    if (distance > (float) radius) {
                                        runOnUiThread(() -> Toast.makeText(MapActivity.this, "超出位置，无法签到", Toast.LENGTH_SHORT).show());
                                    } else {
                                        runOnUiThread(() -> {
                                            Intent intent = new Intent(MapActivity.this, FaceRecognitionActivity.class);
                                            intent.putExtra("modelAssetName", "mobile_face_net.tflite");
                                            startActivity(intent);
                                            finish();
                                        });
                                    }
                                }
                            } else {
                                String info = String.valueOf(loc.getClass().getMethod("getErrorInfo").invoke(loc));
                                runOnUiThread(() -> Toast.makeText(MapActivity.this, "定位失败: " + info, Toast.LENGTH_SHORT).show());
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(MapActivity.this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show());
                        }
                    }
                    return null;
                }
            });
            clientCls.getMethod("setLocationListener", listenerCls).invoke(locationClient, listener);
            clientCls.getMethod("startLocation").invoke(locationClient);
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MapActivity.this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show());
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

    private boolean isClassPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
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
    protected void onResume() {
        super.onResume();
        if (amapAvailable && mapView != null) {
            try {
                mapView.getClass().getMethod("onResume").invoke(mapView);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (amapAvailable && mapView != null) {
            try {
                mapView.getClass().getMethod("onPause").invoke(mapView);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (amapAvailable && mapView != null) {
            try {
                mapView.getClass().getMethod("onSaveInstanceState", Bundle.class).invoke(mapView, outState);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (amapAvailable && locationClient != null) {
            try {
                locationClient.getClass().getMethod("stopLocation").invoke(locationClient);
                locationClient.getClass().getMethod("onDestroy").invoke(locationClient);
            } catch (Exception ignored) {}
        }
        if (amapAvailable && mapView != null) {
            try {
                mapView.getClass().getMethod("onDestroy").invoke(mapView);
            } catch (Exception ignored) {}
        }
    }
}
