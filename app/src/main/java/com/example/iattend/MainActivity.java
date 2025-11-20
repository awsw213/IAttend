package com.example.iattend;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.iattend.data.remote.SupabaseClient;
import com.example.iattend.data.remote.config.SupabaseConfig;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import com.example.iattend.ui.ProgressArcView;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();
    private LocationManager locationManager;
    private Location lastLocation;
    private String pendingCode;
    private SessionInfo pendingSession;
    private WebView webView;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webMap);
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
        btnDoCheckIn.setOnClickListener(v -> { if (pendingSession == null) showCodeDialog(); else confirmCheckIn(); });

        tvHome = findViewById(R.id.tvHome);

        tvHistory = findViewById(R.id.tvHistory);

        tvPersonal = findViewById(R.id.tvPersonal);
        findViewById(R.id.navHome).setOnClickListener(v -> selectTab(0));
        findViewById(R.id.navHistory).setOnClickListener(v -> selectTab(1));
        findViewById(R.id.navPersonalCentre).setOnClickListener(v -> selectTab(2));
        selectTab(0);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        initMap();
        fetchProgress("user-001", value -> runOnUiThread(() -> progressArc.animateTo(value)));
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
            if (session.end_ms != null && now > session.end_ms) {
                Toast.makeText(this, getString(R.string.code_expired), Toast.LENGTH_SHORT).show();
                return;
            }
            if (session.start_ms != null && now < session.start_ms) {
                Toast.makeText(this, getString(R.string.out_of_time), Toast.LENGTH_SHORT).show();
                return;
            }
            pendingCode = code;
            pendingSession = session;
            tvSessionInfo.setText(getString(R.string.session_info, safe(session.course_name), remainingString(session.end_ms), code));
            setFenceOnMap(session.center_lat, session.center_lon, session.radius_m);
            ensureLocationPermission();
            startCountdown(session.end_ms);
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
            startLocationUpdates();
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
                startLocationUpdates();
            } else {
                Toast.makeText(this, getString(R.string.unable_get_location), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startLocationUpdates() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 2, this);
            }
        } catch (Exception ignored) {}
        updateWithLastKnown();
    }

    private void updateWithLastKnown() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (loc != null) onLocationChanged(loc);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        if (webView != null) {
            webView.evaluateJavascript("updateUserLocation(" + location.getLatitude() + "," + location.getLongitude() + ")", null);
        }
        if (pendingSession != null && tvSessionInfo != null) {
            double d = distanceMeters(location.getLatitude(), location.getLongitude(), pendingSession.center_lat, pendingSession.center_lon);
            tvSessionInfo.setText(getString(R.string.session_info_with_distance, safe(pendingSession.course_name), remainingString(pendingSession.end_ms), pendingCode, (int) d));
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
        double d = distanceMeters(lastLocation.getLatitude(), lastLocation.getLongitude(), pendingSession.center_lat, pendingSession.center_lon);
        double radius = pendingSession.radius_m != null ? pendingSession.radius_m : 0.0;
        long now = System.currentTimeMillis();
        if (radius > 0 && d > radius) {
            Toast.makeText(this, getString(R.string.out_of_range), Toast.LENGTH_SHORT).show();
            return;
        }
        if (pendingSession.end_ms != null && now > pendingSession.end_ms) {
            Toast.makeText(this, getString(R.string.session_finished), Toast.LENGTH_SHORT).show();
            return;
        }
        if (pendingSession.start_ms != null && now < pendingSession.start_ms) {
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
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        String html = "<html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<style>html,body,#map{height:100%;margin:0;padding:0}</style>" +
                "</head><body><div id='map'></div>" +
                "<script>" +
                "var map=L.map('map').setView([0,0],16);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);" +
                "var userMarker=L.marker([0,0]);" +
                "var fence=null;" +
                "function updateUserLocation(lat,lon){if(!userMarker._map){userMarker.addTo(map)}userMarker.setLatLng([lat,lon]);}" +
                "function setFence(lat,lon,r){if(fence){map.removeLayer(fence)}fence=L.circle([lat,lon],{radius:r,color:'green',fillColor:'#3f3',fillOpacity:0.2}).addTo(map);map.panTo([lat,lon]);}" +
                "</script></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void setFenceOnMap(double lat, double lon, double radius) {
        if (webView != null) {
            webView.evaluateJavascript("setFence(" + lat + "," + lon + "," + radius + ")", null);
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
                long sec = Math.max(0, remain / 1000);
                btnDoCheckIn.setText(getString(R.string.check_in_with_seconds, sec));
                if (remain > 0) countdownHandler.postDelayed(this, 1000);
            }
        };
        countdownHandler.post(countdownTask);
    }

    private String safe(String s) { return s == null ? "" : s; }

    private CompletableFuture<SessionInfo> fetchSessionInfo(String code) {
        return CompletableFuture.supplyAsync(() -> {
            String url = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.SESSIONS_TABLE + "?code=eq." + code + "&limit=1";
            Request.Builder builder = new Request.Builder().url(url).addHeader("apikey", SupabaseConfig.SUPABASE_KEY).addHeader("Accept", "application/json");
            String token = SupabaseClient.getInstance().getCurrentToken();
            if (token != null) builder.addHeader("Authorization", "Bearer " + token);
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
                    if (minutes < 1) minutes = 5;
                    if (minutes > 30) minutes = 30;
                    if (name.isEmpty() || (expected <= 0 && selectedUserIds.isEmpty())) {
                        Toast.makeText(this, getString(R.string.please_complete_fields), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int finalExpected = !selectedUserIds.isEmpty() ? selectedUserIds.size() : expected;
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
        updateWithLastKnown();
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
                .thenCompose(code -> SupabaseClient.getInstance().createAttendSession(code, name, !selectedUserIds.isEmpty() ? selectedUserIds.size() : expected, now, end, lat, lon, radius, selectedUserIds))
                .thenAccept(code -> runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.code_generated, code), Toast.LENGTH_LONG).show();
                    tvSessionInfo.setText(getString(R.string.session_info, name, remainingString(end), code));
                    setFenceOnMap(lat, lon, radius);
                    startCountdown(end);
                    startStatsPolling(code);
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
        public String code;
        public Double center_lat;
        public Double center_lon;
        public Double radius_m;
        public Long start_ms;
        public Long end_ms;
        public String course_name;
    }

    private void startStatsPolling(String code) {
        if (statsTask != null) statsHandler.removeCallbacks(statsTask);
        statsTask = new Runnable() {
            @Override public void run() {
                loadSessionStats(code);
                statsHandler.postDelayed(this, 5000);
            }
        };
        statsHandler.post(statsTask);
    }

    private void loadSessionStats(String code) {
        SupabaseClient.getInstance().fetchSessionStats(code)
                .thenAccept(stats -> runOnUiThread(() -> {
                    tvStats.setText(getString(R.string.stats_format, stats.checkedCount, stats.expectedCount));
                    if (stats.unsignedUsers != null && !stats.unsignedUsers.isEmpty()) {
                        tvNoUnsigned.setVisibility(View.GONE);
                    } else {
                        tvNoUnsigned.setVisibility(View.VISIBLE);
                    }
                    unsignedAdapter.setItems(stats.unsignedUsers);
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