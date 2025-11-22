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

import com.example.iattend.data.remote.SupabaseClient;
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
    private String sessionCode;
    private long signAttemptAt;
    private long expireAtMs;

    public static void start(Context context, double lat, double lon, double radius, String sessionCode, long expireAtMs) {
        Intent intent = new Intent(context, MapActivity.class);
        intent.putExtra("lat", lat);
        intent.putExtra("lon", lon);
        intent.putExtra("radius", radius);
        intent.putExtra("sessionCode", sessionCode);
        intent.putExtra("expireAtMs", expireAtMs);
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
        sessionCode = getIntent().getStringExtra("sessionCode");
        sessionCode = getIntent().getStringExtra("sessionCode");
        expireAtMs = getIntent().getLongExtra("expireAtMs", 0L);
        amapAvailable = isClassPresent("com.amap.api.maps.MapView");
        if (amapAvailable) {
            try {
                Class<?> mi = Class.forName("com.amap.api.maps.MapsInitializer");
                Method show = mi.getMethod("updatePrivacyShow", android.content.Context.class, boolean.class, boolean.class);
                Method agree = mi.getMethod("updatePrivacyAgree", android.content.Context.class, boolean.class);
                show.invoke(null, this, true, true);
                agree.invoke(null, this, true);
                Class<?> lc = Class.forName("com.amap.api.location.AMapLocationClient");
                Method lShow = lc.getMethod("updatePrivacyShow", android.content.Context.class, boolean.class, boolean.class);
                Method lAgree = lc.getMethod("updatePrivacyAgree", android.content.Context.class, boolean.class);
                lShow.invoke(null, this, true, true);
                lAgree.invoke(null, this, true);
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
            info.setText(getString(R.string.map_sdk_missing));
            info.setTextSize(16f);
            RelativeLayout.LayoutParams ip = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            ip.addRule(RelativeLayout.CENTER_IN_PARENT);
            root.addView(info, ip);
            Button fallbackBtn = new Button(this);
            fallbackBtn.setText(getString(R.string.check_in));
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
        signAttemptAt = System.currentTimeMillis();
        android.util.Log.d("MapSign", "sign attempt at=" + signAttemptAt);
        if (expireAtMs > 0 && System.currentTimeMillis() > expireAtMs) {
            isSign = false;
            if (btnSignIn != null) {
                btnSignIn.setEnabled(false);
                btnSignIn.setBackgroundColor(Color.GRAY);
            }
            Toast.makeText(this, getString(R.string.code_expired), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isSystemLocationEnabled()) {
            isSign = false;
            return;
        }
        if (locationClient != null && amapAvailable) {
            try {
                Method stop = locationClient.getClass().getMethod("stopLocation");
                Method start = locationClient.getClass().getMethod("startLocation");
                stop.invoke(locationClient);
                start.invoke(locationClient);
            } catch (Exception e) {
                isSign = false;
                android.util.Log.d("MapSign", "sign restart exception: " + (e.getMessage() != null ? e.getMessage() : ""));
            }
        } else if (!amapAvailable) {
            Toast.makeText(this, "缺少地图定位组件", Toast.LENGTH_SHORT).show();
            isSign = false;
        } else {
            if (hasLocationPermission()) {
                try {
                    startLocation();
                } catch (Exception e) {
                    isSign = false;
                    android.util.Log.d("MapSign", "sign start exception: " + (e.getMessage() != null ? e.getMessage() : ""));
                }
            } else {
                requestLocationPermission();
            }
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
            locationClient = cons.newInstance(getApplicationContext());
            Class<?> optionCls = Class.forName("com.amap.api.location.AMapLocationClientOption");
            Object option = optionCls.getConstructor().newInstance();
            Class<?> modeCls = Class.forName("com.amap.api.location.AMapLocationClientOption$AMapLocationMode");
            Field high = modeCls.getField("Hight_Accuracy");
            Object mode = high.get(null);
            Method setMode = optionCls.getMethod("setLocationMode", modeCls);
            setMode.invoke(option, mode);
            optionCls.getMethod("setOnceLocation", boolean.class).invoke(option, false);
            optionCls.getMethod("setInterval", long.class).invoke(option, 5000L);
            optionCls.getMethod("setNeedAddress", boolean.class).invoke(option, true);
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
                                mo = moCls.getMethod("title", String.class).invoke(mo, getString(R.string.current_position));
                                mo = moCls.getMethod("snippet", String.class).invoke(mo, getString(R.string.position_snippet_format, latitude, longitude));
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
                                        // Log distance failure to sign_in_logs with actual location data
                                        logFailure("fail_geo", "超出位置范围，距离: " + (int)distance + "米，限制: " + (int)radius + "米", latitude, longitude, (int) distance);
                                        runOnUiThread(() -> Toast.makeText(MapActivity.this, "超出位置，无法签到", Toast.LENGTH_SHORT).show());
                                    } else {
                                        runOnUiThread(() -> {
                                            Intent intent = new Intent(MapActivity.this, FaceRecognitionActivity.class);
                                            intent.putExtra("modelAssetName", "mobile_face_net.tflite");
                                            intent.putExtra("sessionCode", sessionCode);
                                            intent.putExtra("latitude", latitude);
                                            intent.putExtra("longitude", longitude);
                                            intent.putExtra("distance", (int) distance);
                                            startActivity(intent);
                                            finish();
                                        });
                                    }
                                }
                            } else {
                                String info = String.valueOf(loc.getClass().getMethod("getErrorInfo").invoke(loc));
                                android.util.Log.d("MapSign", "loc error: " + info);
                                isSign = false;
                                // Log location failure to sign_in_logs (without valid location data)
                                logFailure("fail_geo", "定位失败: " + info, 0, 0, 0);
                                runOnUiThread(() -> {
                                    Toast.makeText(MapActivity.this, "定位失败: " + info, Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (Exception e) {
                            isSign = false;
                            android.util.Log.d("MapSign", "loc exception: " + (e.getMessage() != null ? e.getMessage() : ""));
                        }
                    }
                    return null;
                }
            });
            clientCls.getMethod("setLocationListener", listenerCls).invoke(locationClient, listener);
            clientCls.getMethod("startLocation").invoke(locationClient);
        } catch (Exception e) {
            isSign = false;
            android.util.Log.d("MapSign", "startLocation exception: " + (e.getMessage() != null ? e.getMessage() : ""));
            Toast.makeText(this, "定位服务启动失败，请检查定位权限及组件", Toast.LENGTH_SHORT).show();
            if (btnSignIn != null) {
                btnSignIn.setEnabled(false);
                btnSignIn.setBackgroundColor(Color.GRAY);
            }
        }
    }

    private boolean isSystemLocationEnabled() {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gps = lm != null && lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        boolean network = lm != null && lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        return gps || network;
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
            }
        }
    }

    private void startCountDown() {
        long remain = expireAtMs > 0 ? Math.max(0, expireAtMs - System.currentTimeMillis()) : 261000;
        countDownTimer = new CountDownTimer(remain, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.seconds_format, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.seconds_format, 0));
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

    /**
     * Log a sign-in failure to sign_in_logs table
     */
    private void logFailure(String failReason, String errorInfo, double latitude, double longitude, int distanceMeters) {
        new Thread(() -> {
            try {
                SupabaseClient.getInstance()
                    .submitFailedCheckIn(sessionCode, latitude, longitude, distanceMeters, System.currentTimeMillis(), failReason)
                    .thenAccept(success -> {
                        if (!success) {
                            android.util.Log.e("MapActivity", "Failed to log failure to sign_in_logs: " + errorInfo);
                        }
                    })
                    .exceptionally(t -> {
                        android.util.Log.e("MapActivity", "Exception logging failure: " + errorInfo, t);
                        return null;
                    })
                    .join();
            } catch (Exception e) {
                android.util.Log.e("MapActivity", "Error logging failure: " + errorInfo, e);
            }
        }).start();
    }
}
