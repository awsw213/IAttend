package com.example.iattend;

import android.Manifest;
import android.app.AlertDialog;
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

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.Circle;
import com.amap.api.maps.model.CircleOptions;
import android.graphics.Color;
import com.example.iattend.data.remote.SupabaseClient;
import com.example.iattend.data.remote.config.SupabaseConfig;
import com.example.iattend.ui.ProgressArcView;
import com.google.gson.Gson;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.PermissionLists;

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

public class MainActivity extends AppCompatActivity implements AMapLocationListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private String pendingCode;
    private SessionInfo pendingSession;
    private MapView mapView;
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
    private AMap aMap;
    private AMapLocationClient mLocationClient;
    private AMapLocationClientOption mLocationOption;
    private String mCurrentDetailedAddress;
    private LatLng mCurrentLatLng;
    private AMapLocation lastLocation;
    private boolean isFirstLoc = true;
    private Circle fenceCircle;
    private Double pendingOpenLat, pendingOpenLon, pendingOpenRadius;
    private boolean pendingOpenMap = false;

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

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
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
        selectTab(0);

        XXPermissions.with(this)
                .permission(PermissionLists.getAccessFineLocationPermission())
                .request((grantedList, deniedList) -> {
                    boolean allGranted = deniedList.isEmpty();
                    if (!allGranted) {
                        boolean doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(MainActivity.this, deniedList);
                        if (doNotAskAgain) {
                            XXPermissions.startPermissionActivity(MainActivity.this);
                        }
                        return;
                    }
                    initMap();
                });

        fetchProgress("user-001", value -> runOnUiThread(() -> progressArc.animateTo(value)));
    }

    /**
     * 初始化 定位配置
     */
    private void initLocationConfig() {
        try {
            //初始化定位
            mLocationClient = new AMapLocationClient(getApplicationContext());
            //设置定位回调监听
            mLocationClient.setLocationListener(this);
            //初始化定位参数
            mLocationOption = new AMapLocationClientOption();
            //设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //设置是否返回地址信息（默认返回地址信息）
            mLocationOption.setNeedAddress(true);
            //设置是否只定位一次,默认为false
            mLocationOption.setOnceLocation(true);
            mLocationOption.setHttpTimeOut(30000);
            //设置是否强制刷新WIFI，默认为强制刷新
            mLocationOption.setWifiActiveScan(true);
            //设置是否允许模拟位置,默认为false，不允许模拟位置
            mLocationOption.setMockEnable(false);
            //设置定位间隔,单位毫秒,默认为2000ms
            mLocationOption.setInterval(4000);
            //给定位客户端对象设置定位参数
            mLocationClient.setLocationOption(mLocationOption);
            //多次激活，最好调用一次stop，再调用start以保证场景模式生效
            //  mLocationClient.stopLocation();
            // 开始定位
            mLocationClient.startLocation();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            Long endMs = parseIsoToMillis(session.expires_at);
            if (endMs != null && now > endMs) {
                Toast.makeText(this, getString(R.string.code_expired), Toast.LENGTH_SHORT).show();
                return;
            }
            Long startMs = parseIsoToMillis(session.created_at);
            if (startMs != null && now < startMs) {
                Toast.makeText(this, getString(R.string.out_of_time), Toast.LENGTH_SHORT).show();
                return;
            }
            pendingCode = code;
            pendingSession = session;
            tvSessionInfo.setText(getString(R.string.session_info, safe(session.course_name), remainingString(endMs), code));
            double cLat = session.location_data != null && session.location_data.lat != null ? session.location_data.lat : session.center_lat;
            double cLon = session.location_data != null && session.location_data.lon != null ? session.location_data.lon : session.center_lon;
            double rad = session.location_data != null && session.location_data.radius_m != null ? session.location_data.radius_m : (session.radius_m != null ? session.radius_m : 0.0);
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
            startCountdown(endMs);
            startStatsPolling(code);
            confirmCheckIn();
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
        if (lastLocation == null) {
            Toast.makeText(this, getString(R.string.enable_gps_retry), Toast.LENGTH_SHORT).show();
            return;
        }
        double cLat = pendingSession.location_data != null && pendingSession.location_data.lat != null ? pendingSession.location_data.lat : pendingSession.center_lat;
        double cLon = pendingSession.location_data != null && pendingSession.location_data.lon != null ? pendingSession.location_data.lon : pendingSession.center_lon;
        double radius = pendingSession.location_data != null && pendingSession.location_data.radius_m != null ? pendingSession.location_data.radius_m : (pendingSession.radius_m != null ? pendingSession.radius_m : 0.0);
        double d = distanceMeters(lastLocation.getLatitude(), lastLocation.getLongitude(), cLat, cLon);
        long now = System.currentTimeMillis();
        if (radius > 0 && d > radius) {
            Toast.makeText(this, getString(R.string.out_of_range), Toast.LENGTH_SHORT).show();
            return;
        }
        Long endMs2 = parseIsoToMillis(pendingSession.expires_at);
        if (endMs2 != null && now > endMs2) {
            Toast.makeText(this, getString(R.string.session_finished), Toast.LENGTH_SHORT).show();
            return;
        }
        Long startMs2 = parseIsoToMillis(pendingSession.created_at);
        if (startMs2 != null && now < startMs2) {
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
                                .submitCheckIn(pendingCode, lastLocation.getLatitude(), lastLocation.getLongitude(), (int) d, System.currentTimeMillis())
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
        if (aMap == null) {
            aMap = mapView.getMap();
        }
        MyLocationStyle myLocationStyle;
        myLocationStyle = new MyLocationStyle();//初始化定位蓝点样式类myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);//连续定位、且将视角移动到地图中心点，定位点依照设备方向旋转，并且会跟随设备移动。（1秒1次定位）如果不设置myLocationType，默认也会执行此种模式。
        myLocationStyle.interval(2000); //设置连续定位模式下的定位间隔，只在连续定位模式下生效，单次定位模式下不会生效。单位为毫秒。
        aMap.setMyLocationStyle(myLocationStyle);//设置定位蓝点的Style
        //aMap.getUiSettings().setMyLocationButtonEnabled(true);设置默认定位按钮是否显示，非必需设置。
        aMap.setMyLocationEnabled(true);// 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。
        initLocationConfig();
//        WebSettings s = webView.getSettings();
//        s.setJavaScriptEnabled(true);
//        s.setDomStorageEnabled(true);
//        String html = "<html><head>" +
//                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
//                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +
//                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
//                "<style>html,body,#map{height:100%;margin:0;padding:0}</style>" +
//                "</head><body><div id='map'></div>" +
//                "<script>" +
//                "var map=L.map('map').setView([0,0],16);" +
//                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);" +
//                "var userMarker=L.marker([0,0]);" +
//                "var fence=null;" +
//                "function updateUserLocation(lat,lon){if(!userMarker._map){userMarker.addTo(map)}userMarker.setLatLng([lat,lon]);}" +
//                "function setFence(lat,lon,r){if(fence){map.removeLayer(fence)}fence=L.circle([lat,lon],{radius:r,color:'green',fillColor:'#3f3',fillOpacity:0.2}).addTo(map);map.panTo([lat,lon]);}" +
//                "</script></body></html>";
//        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void setFenceOnMap(double lat, double lon, double radius) {
        if (aMap == null) {
            return;
        }
        if (fenceCircle != null) {
            fenceCircle.remove();
            fenceCircle = null;
        }
        fenceCircle = aMap.addCircle(new CircleOptions()
                .center(new LatLng(lat, lon))
                .radius(radius)
                .strokeColor(Color.parseColor("#4CAF50"))
                .strokeWidth(3f)
                .fillColor(Color.parseColor("#334CAF50"))
        );
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 15));
    }

    private void openSignInMap(double lat, double lon, double radius) {
        MapActivity.start(MainActivity.this, lat, lon, radius);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        mapView.onDestroy();
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
            mLocationClient.onDestroy();
            mLocationClient = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        mapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        mapView.onSaveInstanceState(outState);
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
        tvHome.setSelected(h);
        tvHistory.setSelected(his);
        tvPersonal.setSelected(p);
    }

    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (amapLocation != null) {
            //定位成功回调信息
            if (amapLocation.getErrorCode() == 0) {
                lastLocation = amapLocation;
                mCurrentDetailedAddress = amapLocation.getAddress();
                if (isFirstLoc) {
                    //设置缩放级别17
                    aMap.moveCamera(CameraUpdateFactory.zoomTo(17));
                    //中心位置为当前坐标
                    aMap.moveCamera(CameraUpdateFactory.changeLatLng(new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude())));
                    isFirstLoc = false;
                }
                if (pendingSession != null && tvSessionInfo != null) {
                    double cLat = pendingSession.location_data != null && pendingSession.location_data.lat != null ? pendingSession.location_data.lat : pendingSession.center_lat;
                    double cLon = pendingSession.location_data != null && pendingSession.location_data.lon != null ? pendingSession.location_data.lon : pendingSession.center_lon;
                    Long endMs = parseIsoToMillis(pendingSession.expires_at);
                    double d = distanceMeters(amapLocation.getLatitude(), amapLocation.getLongitude(), cLat, cLon);
                    tvSessionInfo.setText(getString(R.string.session_info_with_distance, safe(pendingSession.course_name), remainingString(endMs), pendingCode, (int) d));
                }
                // 记录当前定位的坐标
                mCurrentLatLng = new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude());
                //重新移动到中心位置
                //aMap.moveCamera(CameraUpdateFactory.changeLatLng(new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude())));
                Log.e("test-z", "----当前位置：" + amapLocation.getLatitude() + "," + amapLocation.getLongitude() + " ," + mCurrentDetailedAddress);
            } else {
                mCurrentLatLng = null;
            }
        }
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
        if (lastLocation == null) {
            Toast.makeText(this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show();
            return;
        }
        double lat = lastLocation.getLatitude();
        double lon = lastLocation.getLongitude();
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
                    if (!selectedUserIds.isEmpty()) {
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
                    }

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
