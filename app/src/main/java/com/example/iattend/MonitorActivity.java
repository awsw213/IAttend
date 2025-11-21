package com.example.iattend;

import android.os.Bundle;
import android.os.Handler;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.iattend.data.remote.SupabaseClient;
import com.example.iattend.data.remote.config.SupabaseConfig;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MonitorActivity extends AppCompatActivity {
    private TextView tvCourseTitle;
    private TextView tvCode;
    private TextView tvCountdown;
    private com.example.iattend.ui.BarChartView barChart;
    private TextView tvCountDetail;
    private TextView tvRate;
    private TextView tvNoUnsigned;
    private TextView tvUnsignedCount;
    private RecyclerView rvUnsigned;
    private Handler tickerHandler = new Handler();
    private Runnable tickerRunnable;
    private long targetEndMs = -1L;
    private boolean endDialogShown = false;
    private ObjectAnimator blinkAnimator;
    private Handler statsHandler = new Handler();
    private Runnable statsTask;
    private UnsignedAdapter unsignedAdapter;
    private String code;
    private String courseName;
    private String expiresAt;
    private java.util.List<String> selectedUserIds = new java.util.ArrayList<>();
    private java.util.List<com.example.iattend.data.remote.model.UserProfile> cachedProfiles = new java.util.ArrayList<>();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);
        tvCourseTitle = findViewById(R.id.tvCourseTitle);
        tvCode = findViewById(R.id.tvCode);
        tvCountdown = findViewById(R.id.tvCountdown);
        barChart = findViewById(R.id.barChart);
        tvCountDetail = findViewById(R.id.tvCountDetail);
        tvRate = findViewById(R.id.tvRate);
        tvNoUnsigned = findViewById(R.id.tvNoUnsigned);
        tvUnsignedCount = findViewById(R.id.tvUnsignedCount);
        rvUnsigned = findViewById(R.id.rvUnsigned);
        rvUnsigned.setLayoutManager(new LinearLayoutManager(this));
        unsignedAdapter = new UnsignedAdapter();
        rvUnsigned.setAdapter(unsignedAdapter);
        code = getIntent().getStringExtra("code");
        courseName = getIntent().getStringExtra("courseName");
        expiresAt = getIntent().getStringExtra("expires_at");
        java.util.ArrayList<String> sel = getIntent().getStringArrayListExtra("selectedUserIds");
        if (sel != null) selectedUserIds = sel;
        Long endMs = parseIsoToMillis(expiresAt);
        long nowRemain = endMs != null ? Math.max(0, endMs - System.currentTimeMillis()) : 0;
        String remain = formatMMSS(nowRemain);
        tvCourseTitle.setText(safe(courseName));
        tvCode.setText(code);
        tvCountdown.setText("剩余时间 " + remain);
        startCountdown(endMs);
        startStatsPolling(code);
        SupabaseClient.getInstance().fetchAllProfiles()
                .thenAccept(list -> runOnUiThread(() -> {
                    cachedProfiles = list != null ? list : new java.util.ArrayList<>();
                }))
                .exceptionally(t -> { return null; });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTicker();
        if (statsTask != null) statsHandler.removeCallbacks(statsTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTicker();
        stopBlink();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (targetEndMs > 0) startTicker();
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String remainingString(Long endMs) {
        if (endMs == null) return "0:00";
        long diff = endMs - System.currentTimeMillis();
        if (diff <= 0) return "0:00";
        long s = diff / 1000;
        long m = s / 60;
        long r = s % 60;
        return m + ":" + (r < 10 ? ("0" + r) : r);
    }

    private Long parseIsoToMillis(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (Exception ignore1) {
            try {
                return java.time.Instant.parse(iso).toEpochMilli();
            } catch (Exception ignore2) {
                try {
                    return java.time.ZonedDateTime.parse(iso).toInstant().toEpochMilli();
                } catch (Exception ignore3) {
                    try {
                        java.text.SimpleDateFormat f1 = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                        f1.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        return f1.parse(iso).getTime();
                    } catch (Exception ignore4) {
                        try {
                            java.text.SimpleDateFormat f2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                            f2.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                            return f2.parse(iso).getTime();
                        } catch (Exception ignore5) { return null; }
                    }
                }
            }
        }
    }

    private void updateEndIfValid(Long newEnd) {
        if (newEnd == null) return;
        long now = System.currentTimeMillis();
        if (newEnd <= now) return;
        if (targetEndMs <= 0 || Math.abs(newEnd - targetEndMs) > 1000) startCountdown(newEnd);
    }

    private void startCountdown(Long endMs) {
        if (endMs == null) { tvCountdown.setText("剩余时间 00:00"); return; }
        targetEndMs = endMs;
        endDialogShown = false;
        tvCode.setVisibility(View.VISIBLE);
        stopBlink();
        startTicker();
    }

    private String formatMMSS(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String mm = minutes < 10 ? ("0" + minutes) : String.valueOf(minutes);
        String ss = seconds < 10 ? ("0" + seconds) : String.valueOf(seconds);
        return mm + ":" + ss;
    }

    private void startTicker() {
        if (tickerRunnable != null) tickerHandler.removeCallbacks(tickerRunnable);
        tickerRunnable = new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                long remain = targetEndMs - now;
                if (remain <= 0) {
                    tvCountdown.setText("剩余时间 00:00");
                    stopBlink();
                    if (!endDialogShown) {
                        endDialogShown = true;
                        closeSignIn();
                    }
                    return;
                }
                tvCountdown.setText("剩余时间 " + formatMMSS(remain));
                if (remain <= 60_000L) startBlink(); else stopBlink();
                tickerHandler.postDelayed(this, 1000);
            }
        };
        tickerHandler.post(tickerRunnable);
    }

    private void stopTicker() {
        if (tickerRunnable != null) tickerHandler.removeCallbacks(tickerRunnable);
    }

    private void startBlink() {
        if (blinkAnimator != null && blinkAnimator.isRunning()) return;
        tvCountdown.setTextColor(0xFFD32F2F);
        blinkAnimator = ObjectAnimator.ofFloat(tvCountdown, "alpha", 1f, 0.5f);
        blinkAnimator.setRepeatMode(ValueAnimator.REVERSE);
        blinkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        blinkAnimator.setDuration(600);
        blinkAnimator.start();
    }

    private void stopBlink() {
        if (blinkAnimator != null) {
            blinkAnimator.cancel();
            blinkAnimator = null;
        }
        tvCountdown.setAlpha(1f);
        tvCountdown.setTextColor(ContextCompat.getColor(this, R.color.blue_500));
    }

    private void closeSignIn() {
        tvCode.setVisibility(View.GONE);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("签到已结束")
                .setMessage("本次签到已关闭")
                .setPositiveButton("知道了", (d, w) -> d.dismiss())
                .show();
    }

    private void refreshSessionFromServer(String sessionCode) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.SESSIONS_TABLE + "?sign_in_code=eq." + sessionCode + "&limit=1";
                Request.Builder builder = new Request.Builder().url(url).addHeader("apikey", SupabaseConfig.SUPABASE_KEY).addHeader("Accept", "application/json");
                String token = SupabaseClient.getInstance().getCurrentToken();
                if (token != null) builder.addHeader("Authorization", "Bearer " + token);
                else builder.addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_KEY);
                Request request = builder.get().build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        SessionInfo[] arr = gson.fromJson(body, SessionInfo[].class);
                        if (arr != null && arr.length > 0) {
                            SessionInfo s = arr[0];
                            runOnUiThread(() -> {
                                courseName = s.course_name != null ? s.course_name : courseName;
                                expiresAt = s.expires_at != null ? s.expires_at : expiresAt;
                                tvCourseTitle.setText(safe(courseName));
                                if (s.sign_in_code != null) tvCode.setText(s.sign_in_code);
                                Long end = parseIsoToMillis(expiresAt);
                                if (end == null && s.created_at != null && s.duration_minutes > 0) {
                                    Long created = parseIsoToMillis(s.created_at);
                                    if (created != null) end = created + s.duration_minutes * 60_000L;
                                }
                                updateEndIfValid(end);
                            });
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
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
                    int expectedTotal = stats.expectedCount;
                    barChart.setData(stats.checkedCount, expectedTotal);
                    tvCountDetail.setText(getString(R.string.stats_format, stats.checkedCount, expectedTotal));
                    int rate = expectedTotal > 0 ? Math.round((stats.checkedCount * 100f) / expectedTotal) : 0;
                    tvRate.setText(rate + "%");
                    refreshSessionFromServer(code);
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
                    tvUnsignedCount.setText(unsigned.size() + " 人");
                    unsignedAdapter.setItems(unsigned);
                }))
                .exceptionally(t -> { return null; });
    }

    private static class SessionInfo {
        public String sign_in_code;
        public String course_name;
        public String expires_at;
        public String created_at;
        public int duration_minutes;
    }

    private static class UnsignedAdapter extends RecyclerView.Adapter<UnsignedAdapter.VH> {
        private java.util.List<com.example.iattend.data.remote.model.UserProfile> items = new java.util.ArrayList<>();
        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(TextView t) { super(t); tv = t; }
        }
        @Override public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            TextView t = new TextView(parent.getContext());
            t.setPadding(24, 16, 24, 16);
            t.setTextSize(16f);
            t.setClickable(true);
            t.setBackgroundResource(android.R.drawable.list_selector_background);
            return new VH(t);
        }
        @Override public void onBindViewHolder(VH holder, int position) {
            com.example.iattend.data.remote.model.UserProfile u = items.get(position);
            String name = u.getName() != null ? u.getName() : "";
            String email = u.getEmail() != null ? u.getEmail() : "";
            holder.tv.setText(name.isEmpty() ? email : name);
        }
        @Override public int getItemCount() { return items.size(); }
        void setItems(java.util.List<com.example.iattend.data.remote.model.UserProfile> list) {
            items = list != null ? list : new java.util.ArrayList<>();
            notifyDataSetChanged();
        }
    }
}
