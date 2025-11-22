package com.example.iattend;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.graphics.Color;
import com.example.iattend.data.remote.SupabaseClient;
import com.example.iattend.data.remote.config.SupabaseConfig;
import com.example.iattend.ui.ProgressArcView;
import com.google.gson.Gson;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private String pendingCode;
    private SessionInfo pendingSession;
    private View mapView;
    private Button btnDoCheckIn;
    private TextView tvSessionInfo;

    private TextView tvHome, tvHistory, tvPersonal;
    private Handler countdownHandler = new Handler();
    private Runnable countdownTask;

    private RecyclerView rvUnsigned;
    private TextView tvStats;
    private TextView tvNoUnsigned;
    private Handler statsHandler = new Handler();
    private Runnable statsTask;
    private UnsignedAdapter unsignedAdapter;
    private java.util.List<String> selectedUserIds = new java.util.ArrayList<>();
    private java.util.List<com.example.iattend.data.remote.model.UserProfile> cachedProfiles = new java.util.ArrayList<>();
    private Object aMap;
    private Object mLocationClient;
    private Object mLocationOption;
    private String mCurrentDetailedAddress;
    private double lastLat;
    private double lastLon;
    private boolean hasLocation;
    private boolean isFirstLoc = true;
    private Object fenceCircle;
    private Double pendingOpenLat, pendingOpenLon, pendingOpenRadius;
    private boolean pendingOpenMap = false;
    private boolean amapAvailable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        View mapContainer = findViewById(R.id.mapContainer);
        try {
            Class<?> mi = Class.forName("com.amap.api.maps.MapsInitializer");
            Method show = mi.getMethod("updatePrivacyShow", android.content.Context.class, boolean.class, boolean.class);
            Method agree = mi.getMethod("updatePrivacyAgree", android.content.Context.class, boolean.class);
            show.invoke(null, this, true, true);
            agree.invoke(null, this, true);
        } catch (Exception ignored) {}
        amapAvailable = isClassPresent("com.amap.api.maps.MapView");
        if (amapAvailable && mapContainer != null) {
            try {
                Class<?> mvCls = Class.forName("com.amap.api.maps.MapView");
                mapView = (android.view.View) mvCls.getConstructor(android.content.Context.class).newInstance(this);
                if (mapView instanceof android.view.View) {
                    ((android.view.View) mapView).setLayoutParams(new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
                    ((android.widget.FrameLayout) mapContainer).addView((android.view.View) mapView);
                }
                mapView.getClass().getMethod("onCreate", Bundle.class).invoke(mapView, savedInstanceState);
            } catch (Exception ignored) {}
        }
        btnDoCheckIn = findViewById(R.id.btnDoCheckIn);
        tvSessionInfo = findViewById(R.id.tvSessionInfo);
        ProgressArcView progressArc = findViewById(R.id.progressArc);
        rvUnsigned = findViewById(R.id.rvUnsigned);
        tvStats = findViewById(R.id.tvStats);
        tvNoUnsigned = findViewById(R.id.tvNoUnsigned);
        rvUnsigned.setLayoutManager(new LinearLayoutManager(this));
        unsignedAdapter = new UnsignedAdapter();
        rvUnsigned.setAdapter(unsignedAdapter);
        findViewById(R.id.btnCreate).setOnClickListener(v -> showCreateSessionDialog());
        btnDoCheckIn.setOnClickListener(v -> showCodeDialog());

        tvHome = findViewById(R.id.tvHome);

        tvHistory = findViewById(R.id.tvHistory);

        tvPersonal = findViewById(R.id.tvPersonal);

        findViewById(R.id.navHome).setOnClickListener(v -> selectTab(0));
        findViewById(R.id.navHistory).setOnClickListener(v -> selectTab(1));
        findViewById(R.id.navPersonalCentre).setOnClickListener(v -> selectTab(2));
        // 初始化时高亮首页
        tvHome.setSelected(true);

        // 预加载所有用户 profiles，用于显示未签到用户列表
        SupabaseClient.getInstance().fetchAllProfiles()
                .thenAccept(list -> runOnUiThread(() -> {
                    cachedProfiles = list != null ? list : new java.util.ArrayList<>();
                    Log.d("MainActivity", "Loaded " + cachedProfiles.size() + " user profiles");
                }))
                .exceptionally(t -> {
                    Log.e("MainActivity", "Failed to load user profiles", t);
                    return null;
                });

        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            initMap();
            initLocationConfig();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }

        fetchProgress("user-001", value -> runOnUiThread(() -> progressArc.animateTo(value)));
    }

    /**
     * 初始化 定位配置
     */
    private void initLocationConfig() {
        if (!amapAvailable) return;
        try {
            Class<?> clientCls = Class.forName("com.amap.api.location.AMapLocationClient");
            Constructor<?> cons = clientCls.getConstructor(android.content.Context.class);
            mLocationClient = cons.newInstance(getApplicationContext());
            Class<?> optionCls = Class.forName("com.amap.api.location.AMapLocationClientOption");
            mLocationOption = optionCls.getConstructor().newInstance();
            Class<?> modeCls = Class.forName("com.amap.api.location.AMapLocationClientOption$AMapLocationMode");
            Object mode = modeCls.getField("Hight_Accuracy").get(null);
            optionCls.getMethod("setLocationMode", modeCls).invoke(mLocationOption, mode);
            optionCls.getMethod("setNeedAddress", boolean.class).invoke(mLocationOption, true);
            optionCls.getMethod("setOnceLocation", boolean.class).invoke(mLocationOption, true);
            optionCls.getMethod("setHttpTimeOut", long.class).invoke(mLocationOption, 30000L);
            optionCls.getMethod("setWifiActiveScan", boolean.class).invoke(mLocationOption, true);
            optionCls.getMethod("setMockEnable", boolean.class).invoke(mLocationOption, false);
            optionCls.getMethod("setInterval", long.class).invoke(mLocationOption, 4000L);
            clientCls.getMethod("setLocationOption", optionCls).invoke(mLocationClient, mLocationOption);
            Class<?> listenerCls = Class.forName("com.amap.api.location.AMapLocationListener");
            Object listener = Proxy.newProxyInstance(getClassLoader(), new Class[]{listenerCls}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if (method.getName().equals("onLocationChanged") && args != null && args.length > 0) {
                        Object amapLocation = args[0];
                        try {
                            int err = (int) amapLocation.getClass().getMethod("getErrorCode").invoke(amapLocation);
                            if (err == 0) {
                                lastLat = (double) amapLocation.getClass().getMethod("getLatitude").invoke(amapLocation);
                                lastLon = (double) amapLocation.getClass().getMethod("getLongitude").invoke(amapLocation);
                                hasLocation = true;
                                mCurrentDetailedAddress = String.valueOf(amapLocation.getClass().getMethod("getAddress").invoke(amapLocation));
                                if (isFirstLoc && aMap != null) {
                                    try {
                                        Object zoom = Class.forName("com.amap.api.maps.CameraUpdateFactory").getMethod("zoomTo", float.class).invoke(null, 17f);
                                        aMap.getClass().getMethod("moveCamera", Class.forName("com.amap.api.maps.CameraUpdate")).invoke(aMap, zoom);
                                        Object latLng = Class.forName("com.amap.api.maps.model.LatLng").getConstructor(double.class, double.class).newInstance(lastLat, lastLon);
                                        Object cu = Class.forName("com.amap.api.maps.CameraUpdateFactory").getMethod("changeLatLng", Class.forName("com.amap.api.maps.model.LatLng")).invoke(null, latLng);
                                        aMap.getClass().getMethod("moveCamera", Class.forName("com.amap.api.maps.CameraUpdate")).invoke(aMap, cu);
                                        isFirstLoc = false;
                                    } catch (Exception ignored) {}
                                }
                                if (pendingSession != null && tvSessionInfo != null) {
                                    Double cLatD = pendingSession.location_data != null && pendingSession.location_data.lat != null ? pendingSession.location_data.lat : pendingSession.center_lat;
                                    Double cLonD = pendingSession.location_data != null && pendingSession.location_data.lon != null ? pendingSession.location_data.lon : pendingSession.center_lon;
                                    Long endMsParsed = parseIsoToMillis(pendingSession.expires_at);
                                    Long startMsParsed = parseIsoToMillis(pendingSession.created_at);
                                    Long effectiveEndMs = endMsParsed != null ? endMsParsed : (startMsParsed != null && pendingSession.duration_minutes != null ? startMsParsed + pendingSession.duration_minutes * 60_000L : null);
                                    if (cLatD != null && cLonD != null) {
                                        double d = distanceMeters(lastLat, lastLon, cLatD, cLonD);
                                        runOnUiThread(() -> tvSessionInfo.setText(getString(R.string.session_info_with_distance, safe(pendingSession.course_name), remainingString(effectiveEndMs), pendingCode, (int) d)));
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    return null;
                }
            });
            clientCls.getMethod("setLocationListener", listenerCls).invoke(mLocationClient, listener);
            clientCls.getMethod("startLocation").invoke(mLocationClient);
        } catch (Exception ignored) {}
    }

    private void showCodeDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.enter_code))
                .setView(input)
                .setPositiveButton(getString(R.string.next_step), (d, w) -> performCheckIn(String.valueOf(input.getText())))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void performCheckIn(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            Toast.makeText(this, getString(R.string.invalid_code), Toast.LENGTH_SHORT).show();
            return;
        }
        fetchSessionInfo(code).thenAccept(session -> runOnUiThread(() -> {
            if (session == null) {
                Toast.makeText(this, getString(R.string.invalid_code), Toast.LENGTH_SHORT).show();
                return;
            }
            long now = System.currentTimeMillis();
            Long endMsParsed = parseIsoToMillis(session.expires_at);
            Long startMsParsed = parseIsoToMillis(session.created_at);
            Long effectiveEndMs = endMsParsed != null ? endMsParsed : (startMsParsed != null && session.duration_minutes != null ? startMsParsed + session.duration_minutes * 60_000L : null);
            if (effectiveEndMs != null && now > effectiveEndMs) {
                Toast.makeText(this, getString(R.string.code_expired), Toast.LENGTH_SHORT).show();
                return;
            }
            if (startMsParsed != null && now < startMsParsed) {
                Toast.makeText(this, getString(R.string.out_of_time), Toast.LENGTH_SHORT).show();
                return;
            }
            pendingCode = code;
            pendingSession = session;
            tvSessionInfo.setText(getString(R.string.session_info, safe(session.course_name), remainingString(effectiveEndMs), code));
            Double cLatD = session.location_data != null && session.location_data.lat != null ? session.location_data.lat : session.center_lat;
            Double cLonD = session.location_data != null && session.location_data.lon != null ? session.location_data.lon : session.center_lon;
            Double radD = session.location_data != null && session.location_data.radius_m != null ? session.location_data.radius_m : session.radius_m;
            if (cLatD == null || cLonD == null || radD == null) {
                Toast.makeText(this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show();
                return;
            }
            hasLocation = false;
            double cLat = cLatD;
            double cLon = cLonD;
            double rad = radD;
            boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (fine || coarse) {
                openSignInMap(cLat, cLon, rad);
                initLocationConfig();
            } else {
                pendingOpenLat = cLat;
                pendingOpenLon = cLon;
                pendingOpenRadius = rad;
                pendingOpenMap = true;
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            }
            if (effectiveEndMs != null) startCountdown(effectiveEndMs);
            startStatsPolling(code);
        })).exceptionally(t -> {
            runOnUiThread(() -> Toast.makeText(this, getString(R.string.network_failed), Toast.LENGTH_SHORT).show());
            return null;
        });
    }

    private void ensureLocationPermission() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            initLocationConfig();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                initLocationConfig();
                if (pendingOpenMap && pendingOpenLat != null && pendingOpenLon != null && pendingOpenRadius != null) {
                    openSignInMap(pendingOpenLat, pendingOpenLon, pendingOpenRadius);
                }
                pendingOpenMap = false;
                pendingOpenLat = null; pendingOpenLon = null; pendingOpenRadius = null;
            } else {
                Toast.makeText(this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void confirmCheckIn() {
        if (pendingSession == null) {
            Toast.makeText(this, getString(R.string.please_enter_code), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasLocation) {
            Toast.makeText(this, getString(R.string.enable_gps_retry), Toast.LENGTH_SHORT).show();
            return;
        }
        Double cLatD = pendingSession.location_data != null && pendingSession.location_data.lat != null ? pendingSession.location_data.lat : pendingSession.center_lat;
        Double cLonD = pendingSession.location_data != null && pendingSession.location_data.lon != null ? pendingSession.location_data.lon : pendingSession.center_lon;
        Double radiusD = pendingSession.location_data != null && pendingSession.location_data.radius_m != null ? pendingSession.location_data.radius_m : pendingSession.radius_m;
        if (cLatD == null || cLonD == null || radiusD == null) {
            Toast.makeText(this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show();
            return;
        }
        double cLat = cLatD;
        double cLon = cLonD;
        double radius = radiusD;
        double d = distanceMeters(lastLat, lastLon, cLat, cLon);
        long now = System.currentTimeMillis();
        if (radius > 0 && d > radius) {
            Toast.makeText(this, getString(R.string.out_of_range), Toast.LENGTH_SHORT).show();
            return;
        }
        Long endMs2Parsed = parseIsoToMillis(pendingSession.expires_at);
        Long startMs2Parsed = parseIsoToMillis(pendingSession.created_at);
        Long effectiveEnd2 = endMs2Parsed != null ? endMs2Parsed : (startMs2Parsed != null && pendingSession.duration_minutes != null ? startMs2Parsed + pendingSession.duration_minutes * 60_000L : null);
        if (effectiveEnd2 != null && now > effectiveEnd2) {
            Toast.makeText(this, getString(R.string.session_finished), Toast.LENGTH_SHORT).show();
            return;
        }
        if (startMs2Parsed != null && now < startMs2Parsed) {
            Toast.makeText(this, getString(R.string.out_of_time), Toast.LENGTH_SHORT).show();
            return;
        }
        String timeStr = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date());
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.please_confirm))
                .setMessage(getString(R.string.check_in_dialog_message, (int) d, timeStr))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                    startFaceRecognition(() -> {
                        SupabaseClient.getInstance()
                                .submitCheckIn(pendingCode, lastLat, lastLon, (int) d, System.currentTimeMillis())
                                .thenAccept(success -> runOnUiThread(() -> {
                                    if (success) {
                                        btnDoCheckIn.setEnabled(false);
                                        btnDoCheckIn.setText(getString(R.string.checked_in));
                                        if (countdownTask != null) countdownHandler.removeCallbacks(countdownTask);
                                        Toast.makeText(this, getString(R.string.check_in_success), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(this, getString(R.string.check_in_report_failed), Toast.LENGTH_SHORT).show();
                                    }
                                }))
                                .exceptionally(t -> {
                                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.check_in_report_failed), Toast.LENGTH_SHORT).show());
                                    return null;
                                });
                    });
                })
                .show();
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void initMap() {
        if (!amapAvailable || mapView == null) return;
        try {
            Method getMap = mapView.getClass().getMethod("getMap");
            aMap = getMap.invoke(mapView);
            Class<?> myStyleCls = Class.forName("com.amap.api.maps.model.MyLocationStyle");
            Object myLocationStyle = myStyleCls.getConstructor().newInstance();
            myStyleCls.getMethod("interval", long.class).invoke(myLocationStyle, 2000L);
            aMap.getClass().getMethod("setMyLocationStyle", myStyleCls).invoke(aMap, myLocationStyle);
            aMap.getClass().getMethod("setMyLocationEnabled", boolean.class).invoke(aMap, true);
        } catch (Exception ignored) {}
    }

    private void setFenceOnMap(double lat, double lon, double radius) {
        if (!amapAvailable || aMap == null) return;
        try {
            if (fenceCircle != null) {
                try { fenceCircle.getClass().getMethod("remove").invoke(fenceCircle); } catch (Exception ignored) {}
                fenceCircle = null;
            }
            Class<?> circleOptsCls = Class.forName("com.amap.api.maps.model.CircleOptions");
            Object opts = circleOptsCls.getConstructor().newInstance();
            Class<?> latLngCls = Class.forName("com.amap.api.maps.model.LatLng");
            Object latLng = latLngCls.getConstructor(double.class, double.class).newInstance(lat, lon);
            Method center = circleOptsCls.getMethod("center", latLngCls);
            Method radiusM = circleOptsCls.getMethod("radius", double.class);
            Method strokeC = circleOptsCls.getMethod("strokeColor", int.class);
            Method strokeW = circleOptsCls.getMethod("strokeWidth", float.class);
            Method fillC = circleOptsCls.getMethod("fillColor", int.class);
            opts = center.invoke(opts, latLng);
            opts = radiusM.invoke(opts, radius);
            opts = strokeC.invoke(opts, Color.parseColor("#4CAF50"));
            opts = strokeW.invoke(opts, 3f);
            opts = fillC.invoke(opts, Color.parseColor("#334CAF50"));
            fenceCircle = aMap.getClass().getMethod("addCircle", circleOptsCls).invoke(aMap, opts);
            Object cu = Class.forName("com.amap.api.maps.CameraUpdateFactory").getMethod("newLatLngZoom", latLngCls, float.class).invoke(null, latLng, 15f);
            aMap.getClass().getMethod("moveCamera", Class.forName("com.amap.api.maps.CameraUpdate")).invoke(aMap, cu);
        } catch (Exception ignored) {}
    }

    private void openSignInMap(double lat, double lon, double radius) {
        MapActivity.start(MainActivity.this, lat, lon, radius, pendingCode);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (amapAvailable && mapView != null) {
            try { mapView.getClass().getMethod("onDestroy").invoke(mapView); } catch (Exception ignored) {}
        }
        if (amapAvailable && mLocationClient != null) {
            try {
                mLocationClient.getClass().getMethod("stopLocation").invoke(mLocationClient);
                mLocationClient.getClass().getMethod("onDestroy").invoke(mLocationClient);
                mLocationClient = null;
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (amapAvailable && mapView != null) {
            try { mapView.getClass().getMethod("onResume").invoke(mapView); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (amapAvailable && mapView != null) {
            try { mapView.getClass().getMethod("onPause").invoke(mapView); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        if (amapAvailable && mapView != null) {
            try { mapView.getClass().getMethod("onSaveInstanceState", Bundle.class).invoke(mapView, outState); } catch (Exception ignored) {}
        }
    }

    private String remainingString(Long endMs) {
        if (endMs == null) return "";
        long diff = endMs - System.currentTimeMillis();
        if (diff <= 0) return "0:00";
        long s = diff / 1000;
        long m = s / 60;
        long r = s % 60;
        return m + ":" + (r < 10 ? ("0" + r) : r);
    }

    private void startCountdown(Long endMs) {
        if (countdownTask != null) countdownHandler.removeCallbacks(countdownTask);
        countdownTask = new Runnable() {
            @Override public void run() {
                long remain = endMs - System.currentTimeMillis();
                btnDoCheckIn.setText(getString(R.string.check_in));
                Long e = parseIsoToMillis(pendingSession != null ? pendingSession.expires_at : null);
                Long showEnd = e != null ? e : endMs;
                String remainStr = remainingString(showEnd);
                if (pendingSession != null && pendingCode != null) {
                    tvSessionInfo.setText(getString(R.string.session_info, safe(pendingSession.course_name), remainStr, pendingCode));
                }
                if (remain > 0) countdownHandler.postDelayed(this, 1000);
            }
        };
        countdownHandler.post(countdownTask);
    }

    private String safe(String s) { return s == null ? "" : s; }

    private CompletableFuture<SessionInfo> fetchSessionInfo(String code) {
        return CompletableFuture.supplyAsync(() -> {
            String url = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.SESSIONS_TABLE + "?sign_in_code=eq." + code + "&limit=1";
            Request.Builder builder = new Request.Builder().url(url).addHeader("apikey", SupabaseConfig.SUPABASE_KEY).addHeader("Accept", "application/json");
            String token = SupabaseClient.getInstance().getCurrentToken();
            if (token != null) {
                builder.addHeader("Authorization", "Bearer " + token);
            } else {
                builder.addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_KEY);
            }
            Request request = builder.get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    SessionInfo[] arr = gson.fromJson(body, SessionInfo[].class);
                    return arr != null && arr.length > 0 ? arr[0] : null;
                } else {
                    throw new IOException(body);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void selectTab(int index) {
        boolean h = index == 0, his = index == 1, p = index == 2;

        // 防止用户快速连续点击
        tvHome.setEnabled(false);
        tvHistory.setEnabled(false);
        tvPersonal.setEnabled(false);

        tvHome.setSelected(h);
        tvHistory.setSelected(his);
        tvPersonal.setSelected(p);

        new android.os.Handler().postDelayed(() -> {
            tvHome.setEnabled(true);
            tvHistory.setEnabled(true);
            tvPersonal.setEnabled(true);
        }, 300);

        Intent intent = null;
        if (h) {
            // 如果已经在首页，不做任何操作
            return;
        } else if (his) {
            // 如果没有活动签到，显示提示
            if (pendingSession == null) {
                android.widget.Toast.makeText(this, getString(R.string.no_active_session), Toast.LENGTH_SHORT).show();
                // 恢复按钮状态
                tvHome.setEnabled(true);
                tvHistory.setEnabled(true);
                tvPersonal.setEnabled(true);
                return;
            }
            intent = new Intent(this, MonitorActivity.class);
            intent.putExtra("code", pendingCode);
            intent.putExtra("courseName", pendingSession.course_name);
            intent.putExtra("expires_at", pendingSession.expires_at);
            intent.putStringArrayListExtra("selectedUserIds", new java.util.ArrayList<>(selectedUserIds));
            reOrderTasks(intent);
        } else if (p) {
            intent = new Intent(this, UserCenterActivity.class);
            reOrderTasks(intent);
        }

        if (intent != null) {
            startActivity(intent);
            overridePendingTransition(0, 0);
        }
    }

    private void reOrderTasks(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private boolean isClassPresent(String name) {
        try { Class.forName(name); return true; } catch (ClassNotFoundException e) { return false; }
    }

    private interface ProgressCallback { void onValue(float v); }

    private void fetchProgress(String userId, ProgressCallback cb) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = "http://10.0.2.2:8080/api/checkin/progress?userId=" + userId;
                Request request = new Request.Builder().url(url).get().build();
                try (Response resp = httpClient.newCall(request).execute()) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        ProgressResp pr = gson.fromJson(body, ProgressResp.class);
                        if (pr != null && pr.code == 200) cb.onValue((float) pr.progress);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void showCreateSessionDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 0);
        EditText etName = new EditText(this);
        etName.setHint(getString(R.string.hint_course_name));
        EditText etCount = new EditText(this);
        etCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCount.setHint(getString(R.string.hint_expected_count));
        EditText etMinutes = new EditText(this);
        etMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMinutes.setHint(getString(R.string.hint_valid_minutes));
        etMinutes.setText("5");
        Button btnSelect = new Button(this);
        btnSelect.setText(getString(R.string.select_participants));
        TextView tvSelected = new TextView(this);
        tvSelected.setText(getString(R.string.selected_count_format, selectedUserIds.size()));
        layout.addView(etName);
        layout.addView(etCount);
        layout.addView(etMinutes);
        layout.addView(btnSelect);
        layout.addView(tvSelected);
        btnSelect.setOnClickListener(v -> showSelectParticipants(tvSelected));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.create_session_title))
                .setView(layout)
                .setPositiveButton(getString(R.string.confirm), (d, w) -> {
                    String name = String.valueOf(etName.getText()).trim();
                    String countStr = String.valueOf(etCount.getText()).trim();
                    String minStr = String.valueOf(etMinutes.getText()).trim();
                    int expected = 0;
                    try { expected = Integer.parseInt(countStr); } catch (Exception ignored) {}
                    int minutes = 5;
                    try { minutes = Integer.parseInt(minStr); } catch (Exception ignored) {}
                    if (minutes < 1) minutes = 1;
                    if (minutes > 30) minutes = 30;
                    if (name.isEmpty() || (expected <= 0 && selectedUserIds.isEmpty())) {
                        Toast.makeText(this, getString(R.string.please_complete_fields), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int finalExpected = expected > 0 ? expected : (!selectedUserIds.isEmpty() ? selectedUserIds.size() : 0);
                    createSession(name, finalExpected, minutes);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showSelectParticipants(TextView tvSelected) {
        Toast.makeText(this, getString(R.string.loading_profiles), Toast.LENGTH_SHORT).show();
        SupabaseClient.getInstance().fetchAllProfiles()
                .thenAccept(list -> runOnUiThread(() -> {
                    cachedProfiles = list != null ? list : new ArrayList<>();
                    CharSequence[] items = new CharSequence[cachedProfiles.size()];
                    boolean[] checks = new boolean[cachedProfiles.size()];
                    for (int i = 0; i < cachedProfiles.size(); i++) {
                        com.example.iattend.data.remote.model.UserProfile u = cachedProfiles.get(i);
                        String name = u.getName() != null && !u.getName().isEmpty() ? u.getName() : (u.getEmail() != null ? u.getEmail() : u.getUserId());
                        items[i] = name;
                        checks[i] = selectedUserIds.contains(u.getUserId());
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.select_participants))
                            .setMultiChoiceItems(items, checks, (dialog, which, isChecked) -> { checks[which] = isChecked; })
                            .setPositiveButton(getString(R.string.confirm), (d, w) -> {
                                selectedUserIds.clear();
                                for (int i = 0; i < cachedProfiles.size(); i++) if (checks[i]) selectedUserIds.add(cachedProfiles.get(i).getUserId());
                                tvSelected.setText(getString(R.string.selected_count_format, selectedUserIds.size()));
                            })
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                }))
                .exceptionally(t -> { runOnUiThread(() -> Toast.makeText(this, getString(R.string.profiles_load_failed), Toast.LENGTH_SHORT).show()); return null; });
    }

    private void createSession(String name, int expected, int minutes) {
        if (!SupabaseClient.getInstance().isUserLoggedIn()) {
            Toast.makeText(this, getString(R.string.please_login_first), Toast.LENGTH_SHORT).show();
            return;
        }
        ensureLocationPermission();
        if (!hasLocation) {
            Toast.makeText(this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show();
            return;
        }
        double lat = lastLat;
        double lon = lastLon;
        long now = System.currentTimeMillis();
        long end = now + minutes * 60_000L;
        double radius = 100.0;
        CompletableFuture.supplyAsync(this::generateUniqueCodeBlocking)
                .thenCompose(code -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, getString(R.string.code_generated, code), Toast.LENGTH_LONG).show();
                        pendingCode = code;
                        SessionInfo s = new SessionInfo();
                        s.sign_in_code = code;
                        s.course_name = name;
                        s.duration_minutes = minutes;
                        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        s.created_at = fmt.format(new java.util.Date(now));
                        s.expires_at = fmt.format(new java.util.Date(end));
                        LocationData loc = new LocationData();
                        loc.lat = lat;
                        loc.lon = lon;
                        loc.radius_m = radius;
                        s.location_data = loc;
                        pendingSession = s;
                        android.content.Intent intent = new android.content.Intent(MainActivity.this, MonitorActivity.class);
                        intent.putExtra("code", code);
                        intent.putExtra("courseName", name);
                        intent.putExtra("expires_at", s.expires_at);
                        intent.putStringArrayListExtra("selectedUserIds", new java.util.ArrayList<>(selectedUserIds));
                        startActivity(intent);
                    });
                    return SupabaseClient.getInstance().createAttendSession(code, name, !selectedUserIds.isEmpty() ? selectedUserIds.size() : expected, minutes, lat, lon, radius);
                })
                .thenAccept(serverCode -> runOnUiThread(() -> {
                    if (serverCode != null && !serverCode.equals(pendingCode)) {
                        pendingCode = serverCode;
                    }
                }))
                .exceptionally(t -> {
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.check_in_report_failed), Toast.LENGTH_SHORT).show());
                    return null;
                });
    }

    private String generateUniqueCodeBlocking() {
        for (int i = 0; i < 10; i++) {
            String code = String.format(Locale.getDefault(), "%06d", (int) (Math.random() * 1000000));
            Boolean exists = SupabaseClient.getInstance().isSessionCodeExists(code).join();
            if (!exists) return code;
        }
        return String.format(Locale.getDefault(), "%06d", (int) (Math.random() * 1000000));
    }

    private static class BaseResp { int code; String msg; }
    private static class ProgressResp { int code; double progress; }

    private static class SessionInfo {
        public String sign_in_code;
        public String course_name;
        public Integer duration_minutes;
        public String created_at;
        public String expires_at;
        public Double center_lat;
        public Double center_lon;
        public Double radius_m;
        public LocationData location_data;
    }
    private static class LocationData { public Double lat; public Double lon; public Double radius_m; }

    private Long parseIsoToMillis(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            java.time.Instant inst = java.time.Instant.parse(iso);
            return inst.toEpochMilli();
        } catch (Exception e) {
            try {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return f.parse(iso).getTime();
            } catch (Exception ex) { return null; }
        }
    }

    private void startStatsPolling(String sign_in_code) {
        if (statsTask != null) statsHandler.removeCallbacks(statsTask);
        statsTask = new Runnable() {
            @Override public void run() {
                loadSessionStats(sign_in_code);
                statsHandler.postDelayed(this, 5000);
            }
        };
        statsHandler.post(statsTask);
    }

    private void loadSessionStats(String code) {
        SupabaseClient.getInstance().fetchSessionStats(code)
                .thenAccept(stats -> runOnUiThread(() -> {
                    int expectedTotal = !selectedUserIds.isEmpty() ? selectedUserIds.size() : stats.expectedCount;
                    tvStats.setText(getString(R.string.stats_format, stats.checkedCount, expectedTotal));

                    java.util.Set<String> checkedIds = new java.util.HashSet<>();
                    if (stats.checkedUsers != null) {
                        for (com.example.iattend.data.remote.model.UserProfile u : stats.checkedUsers) {
                            if (u != null && u.getUserId() != null) checkedIds.add(u.getUserId());
                        }
                    }

                    java.util.List<com.example.iattend.data.remote.model.UserProfile> unsigned = new java.util.ArrayList<>();
                    Log.d("MainActivity", "loadSessionStats: selectedUserIds=" + selectedUserIds.size()
                            + ", cachedProfiles=" + (cachedProfiles != null ? cachedProfiles.size() : 0)
                            + ", checkedIds=" + checkedIds.size());

                    if (!selectedUserIds.isEmpty()) {
                        // 如果创建签到时选择了特定用户，只显示这些用户中未签到的
                        for (String uid : selectedUserIds) {
                            if (!checkedIds.contains(uid)) {
                                com.example.iattend.data.remote.model.UserProfile found = null;
                                if (cachedProfiles != null) {
                                    for (com.example.iattend.data.remote.model.UserProfile p : cachedProfiles) {
                                        if (p != null && uid.equals(p.getUserId())) { found = p; break; }
                                    }
                                }
                                if (found == null) {
                                    found = new com.example.iattend.data.remote.model.UserProfile();
                                    found.setUserId(uid);
                                }
                                unsigned.add(found);
                            }
                        }
                    } else {
                        // 如果没有选择特定用户，显示所有用户中未签到的
                        if (cachedProfiles != null) {
                            for (com.example.iattend.data.remote.model.UserProfile p : cachedProfiles) {
                                if (p != null && !checkedIds.contains(p.getUserId())) {
                                    unsigned.add(p);
                                }
                            }
                        }
                    }

                    Log.d("MainActivity", "loadSessionStats: unsigned.size=" + unsigned.size());

                    if (!unsigned.isEmpty()) {
                        tvNoUnsigned.setVisibility(View.GONE);
                    } else {
                        tvNoUnsigned.setVisibility(View.VISIBLE);
                    }
                    unsignedAdapter.setItems(unsigned);
                }))
                .exceptionally(t -> { return null; });
    }

    private void startFaceRecognition(Runnable onSuccess) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.face_recognition_title))
                .setMessage(getString(R.string.face_recognition_hint))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.face_recognition_pass), (d, w) -> onSuccess.run())
                .show();
    }

    private static class UnsignedAdapter extends RecyclerView.Adapter<UnsignedAdapter.VH> {
        private java.util.List<com.example.iattend.data.remote.model.UserProfile> items = new java.util.ArrayList<>();
        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(TextView t) { super(t); tv = t; }
        }
        @Override public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            TextView t = new TextView(parent.getContext());
            t.setPadding(24, 12, 24, 12);
            t.setTextSize(14f);
            return new VH(t);
        }
        @Override public void onBindViewHolder(VH holder, int position) {
            com.example.iattend.data.remote.model.UserProfile u = items.get(position);
            String name = u.getName() != null ? u.getName() : "";
            String email = u.getEmail() != null ? u.getEmail() : "";
            holder.tv.setText(name.isEmpty() ? email : (name + "  " + email));
        }
        @Override public int getItemCount() { return items.size(); }
        void setItems(java.util.List<com.example.iattend.data.remote.model.UserProfile> list) {
            items = list != null ? list : new java.util.ArrayList<>();
            notifyDataSetChanged();
        }
    }
}
