package com.example.iattend.data.remote;

import android.util.Log;

import com.example.iattend.data.remote.config.SupabaseConfig;
import com.example.iattend.data.remote.model.AuthResponse;
import com.example.iattend.data.remote.model.SignUpRequest;
import com.example.iattend.data.remote.model.UserProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Supabase API客户端
 * 负责与Supabase服务进行网络通信
 */
public class SupabaseClient {
    private static SupabaseClient instance;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private String currentToken;
    private AuthResponse.User currentUser;

    private SupabaseClient() {
        // 配置HTTP客户端
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(s -> {
            Log.d("HTTP", s);
        });
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        this.httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // 配置Gson
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .create();
    }

    /**
     * 获取单例实例
     */
    public static synchronized SupabaseClient getInstance() {
        if (instance == null) {
            instance = new SupabaseClient();
        }
        return instance;
    }

    /**
     * 用户注册
     */
    public CompletableFuture<AuthResponse> signUp(String email, String password, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SignUpRequest signUpRequest = new SignUpRequest(email, password, name);
                String jsonBody = gson.toJson(signUpRequest);

                RequestBody body = RequestBody.create(
                        jsonBody,
                        MediaType.get("application/json")
                );

                Request request = new Request.Builder()
                        .url(SupabaseConfig.AUTH_BASE_URL + SupabaseConfig.SIGNUP_ENDPOINT)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        AuthResponse authResponse = gson.fromJson(responseBody, AuthResponse.class);
                        setCurrentUser(authResponse);
                        return authResponse;
                    } else {
                        throw new IOException("注册失败: " + response.message() + " - " + responseBody);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("注册请求失败", e);
            }
        });
    }

    /**
     * 用户登录
     */
    public CompletableFuture<AuthResponse> signIn(String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> loginData = new HashMap<>();
                loginData.put("email", email);
                loginData.put("password", password);

                String jsonBody = gson.toJson(loginData);

                RequestBody body = RequestBody.create(
                        jsonBody,
                        MediaType.get("application/json")
                );

                Request request = new Request.Builder()
                        .url(SupabaseConfig.AUTH_BASE_URL + SupabaseConfig.LOGIN_ENDPOINT)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        AuthResponse authResponse = gson.fromJson(responseBody, AuthResponse.class);
                        setCurrentUser(authResponse);
                        return authResponse;
                    } else {
                        throw new IOException("登录失败: " + response.message() + " - " + responseBody);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("登录请求失败: " + (e.getMessage() != null ? e.getMessage() : ""), e);
            }
        });
    }

    /**
     * 获取当前用户信息
     */
    public CompletableFuture<UserProfile> getCurrentUserProfile() {
        return CompletableFuture.supplyAsync(() -> {
            if (currentToken == null || currentUser == null) {
                throw new RuntimeException("用户未登录");
            }

            try {
                Request request = new Request.Builder()
                        .url(SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.PROFILES_TABLE +
                             "?user_id=eq." + currentUser.getId())
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + currentToken)
                        .addHeader("Accept", "application/json")
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        UserProfile[] profiles = gson.fromJson(responseBody, UserProfile[].class);
                        if (profiles.length > 0) {
                            return profiles[0];
                        } else {
                            // 如果没有找到profile，创建一个默认的
                            return new UserProfile(currentUser.getId(),
                                    currentUser.getUser_metadata().getName(),
                                    currentUser.getEmail());
                        }
                    } else {
                        throw new IOException("获取用户信息失败: " + response.message() + " - " + responseBody);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("获取用户信息请求失败", e);
            }
        });
    }

    /**
     * 创建或更新用户档案
     */
    public CompletableFuture<UserProfile> upsertUserProfile(UserProfile profile) {
        return CompletableFuture.supplyAsync(() -> {
            if (currentToken == null) {
                throw new RuntimeException("用户未登录");
            }

            try {
                String jsonBody = gson.toJson(profile);
                RequestBody body = RequestBody.create(
                        jsonBody,
                        MediaType.get("application/json")
                );

                Request request = new Request.Builder()
                        .url(SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.PROFILES_TABLE)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + currentToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        UserProfile[] profiles = gson.fromJson(responseBody, UserProfile[].class);
                        return profiles.length > 0 ? profiles[0] : profile;
                    } else {
                        throw new IOException("保存用户信息失败: " + response.message() + " - " + responseBody);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("保存用户信息请求失败", e);
            }
        });
    }

    /**
     * 用户登出
     */
    public void signOut() {
        currentToken = null;
        currentUser = null;
    }

    /**
     * 设置当前用户
     */
    private void setCurrentUser(AuthResponse authResponse) {
        this.currentToken = authResponse.getAccessToken();
        this.currentUser = authResponse.getUser();
    }

    /**
     * 获取当前用户
     */
    public AuthResponse.User getCurrentUser() {
        return currentUser;
    }

    /**
     * 检查用户是否已登录
     */
    public boolean isUserLoggedIn() {
        return currentToken != null && currentUser != null;
    }

    /**
     * 获取当前Token
     */
    public String getCurrentToken() {
        return currentToken;
    }

    public CompletableFuture<Boolean> submitQuickCheckIn(long checkedAtMs) {
        return CompletableFuture.supplyAsync(() -> {
            if (currentToken == null || currentUser == null) {
                throw new RuntimeException("用户未登录");
            }
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", currentUser.getId());
                data.put("checked_at_ms", checkedAtMs);
                String jsonBody = gson.toJson(data);
                RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));
                Request request = new Request.Builder()
                        .url(SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.CHECKINS_TABLE)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + currentToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .post(body)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        return true;
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        throw new IOException("签到上报失败: " + response.message() + " - " + responseBody);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("签到上报请求失败", e);
            }
        });
    }

    public CompletableFuture<Boolean> isEmailRegistered(String email) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.PROFILES_TABLE + "?email=eq." + email + "&select=user_id&limit=1";
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Accept", "application/json")
                        .get()
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        UserProfile[] profiles = gson.fromJson(body, UserProfile[].class);
                        return profiles != null && profiles.length > 0;
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> resendVerificationEmail(String email) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("email", email);
                data.put("type", "signup");
                String jsonBody = gson.toJson(data);
                RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));
                Request request = new Request.Builder()
                        .url(SupabaseConfig.AUTH_BASE_URL + "/resend")
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    return response.isSuccessful();
                }
            } catch (Exception e) {
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> submitCheckIn(String sessionCode, double lat, double lon, int distanceMeters, long checkedAtMs) {
        return CompletableFuture.supplyAsync(() -> {
            if (currentToken == null || currentUser == null) {
                throw new RuntimeException("用户未登录");
            }
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", currentUser.getId());
                data.put("session_code", sessionCode);
                data.put("lat", lat);
                data.put("lon", lon);
                data.put("distance_m", distanceMeters);
                data.put("checked_at_ms", checkedAtMs);
                String jsonBody = gson.toJson(data);
                RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));
                Request request = new Request.Builder()
                        .url(SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.CHECKINS_TABLE)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + currentToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .post(body)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        return true;
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        throw new IOException("签到上报失败: " + response.message() + " - " + responseBody);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("签到上报请求失败", e);
            }
        });
    }

    private static class SessionRow { String code; Integer expected_count; String course_name; String[] expected_user_ids; }
    private static class CheckinRow { String user_id; Long checked_at_ms; }

    public static class SessionStats {
        public int checkedCount;
        public int expectedCount;
        public java.util.List<UserProfile> checkedUsers;
        public java.util.List<UserProfile> unsignedUsers;
    }

    public CompletableFuture<Boolean> isSessionCodeExists(String code) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.SESSIONS_TABLE + "?code=eq." + code + "&select=code&limit=1";
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Accept", "application/json")
                        .get()
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        SessionRow[] rows = gson.fromJson(body, SessionRow[].class);
                        return rows != null && rows.length > 0;
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            }
        });
    }

    public CompletableFuture<String> createAttendSession(String code, String courseName, int expectedCount, long startMs, long endMs, double centerLat, double centerLon, double radiusM, java.util.List<String> expectedUserIds) {
        return CompletableFuture.supplyAsync(() -> {
            if (currentToken == null || currentUser == null) {
                throw new RuntimeException("用户未登录");
            }
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("code", code);
                data.put("course_name", courseName);
                data.put("expected_count", expectedCount);
                data.put("start_ms", startMs);
                data.put("end_ms", endMs);
                data.put("center_lat", centerLat);
                data.put("center_lon", centerLon);
                data.put("radius_m", radiusM);
                data.put("expected_user_ids", expectedUserIds);
                String jsonBody = gson.toJson(data);
                RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));
                Request request = new Request.Builder()
                        .url(SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.SESSIONS_TABLE)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + currentToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(body)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        SessionRow[] rows = gson.fromJson(responseBody, SessionRow[].class);
                        return rows != null && rows.length > 0 ? rows[0].code : code;
                    } else {
                        throw new IOException("创建签到失败: " + response.message() + " - " + responseBody);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("创建签到请求失败", e);
            }
        });
    }

    public CompletableFuture<SessionStats> fetchSessionStats(String sessionCode) {
        return CompletableFuture.supplyAsync(() -> {
            int expected = 0;
            java.util.List<String> expectedIds = new java.util.ArrayList<>();
            try {
                String urlSess = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.SESSIONS_TABLE + "?code=eq." + sessionCode + "&select=expected_count,expected_user_ids&limit=1";
                Request request = new Request.Builder()
                        .url(urlSess)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Accept", "application/json")
                        .get()
                        .build();
                try (Response resp = httpClient.newCall(request).execute()) {
                    String b = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        SessionRow[] rows = gson.fromJson(b, SessionRow[].class);
                        if (rows != null && rows.length > 0) {
                            if (rows[0].expected_count != null) expected = rows[0].expected_count;
                            if (rows[0].expected_user_ids != null) {
                                expectedIds.addAll(java.util.Arrays.asList(rows[0].expected_user_ids));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            java.util.List<String> ids = new java.util.ArrayList<>();
            try {
                String urlChk = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.CHECKINS_TABLE + "?session_code=eq." + sessionCode + "&select=user_id,checked_at_ms";
                Request request = new Request.Builder()
                        .url(urlChk)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                        .addHeader("Accept", "application/json")
                        .get()
                        .build();
                try (Response resp = httpClient.newCall(request).execute()) {
                    String b = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        CheckinRow[] rows = gson.fromJson(b, CheckinRow[].class);
                        if (rows != null) {
                            for (CheckinRow r : rows) if (r != null && r.user_id != null) ids.add(r.user_id);
                        }
                    }
                }
            } catch (Exception ignored) {}

            java.util.List<UserProfile> checked = new java.util.ArrayList<>();
            if (!ids.isEmpty()) {
                StringBuilder in = new StringBuilder();
                for (int i = 0; i < ids.size(); i++) { if (i > 0) in.append(","); in.append(ids.get(i)); }
                try {
                    String urlP = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.PROFILES_TABLE + "?user_id=in.(" + in + ")&select=user_id,name,email";
                    Request request = new Request.Builder().url(urlP).addHeader("apikey", SupabaseConfig.SUPABASE_KEY).addHeader("Accept", "application/json").get().build();
                    try (Response resp = httpClient.newCall(request).execute()) {
                        String b = resp.body() != null ? resp.body().string() : "";
                        if (resp.isSuccessful()) {
                            UserProfile[] rows = gson.fromJson(b, UserProfile[].class);
                            if (rows != null) for (UserProfile u : rows) if (u != null) checked.add(u);
                        }
                    }
                } catch (Exception ignored) {}
            }

            java.util.List<String> unsignedIds = new java.util.ArrayList<>();
            for (String id : expectedIds) if (!ids.contains(id)) unsignedIds.add(id);
            java.util.List<UserProfile> unsigned = new java.util.ArrayList<>();
            if (!unsignedIds.isEmpty()) {
                StringBuilder in2 = new StringBuilder();
                for (int i = 0; i < unsignedIds.size(); i++) { if (i > 0) in2.append(","); in2.append(unsignedIds.get(i)); }
                try {
                    String urlU = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.PROFILES_TABLE + "?user_id=in.(" + in2 + ")&select=user_id,name,email";
                    Request request = new Request.Builder().url(urlU).addHeader("apikey", SupabaseConfig.SUPABASE_KEY).addHeader("Accept", "application/json").get().build();
                    try (Response resp = httpClient.newCall(request).execute()) {
                        String b = resp.body() != null ? resp.body().string() : "";
                        if (resp.isSuccessful()) {
                            UserProfile[] rows = gson.fromJson(b, UserProfile[].class);
                            if (rows != null) for (UserProfile u : rows) if (u != null) unsigned.add(u);
                        }
                    }
                } catch (Exception ignored) {}
            }

            SessionStats stats = new SessionStats();
            stats.checkedCount = ids.size();
            stats.expectedCount = !expectedIds.isEmpty() ? expectedIds.size() : (expected > 0 ? expected : ids.size());
            stats.checkedUsers = checked;
            stats.unsignedUsers = unsigned;
            return stats;
        });
    }

    public CompletableFuture<java.util.List<UserProfile>> fetchAllProfiles() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.PROFILES_TABLE + "?select=user_id,name,email";
                Request.Builder builder = new Request.Builder().url(url).addHeader("apikey", SupabaseConfig.SUPABASE_KEY).addHeader("Accept", "application/json");
                if (currentToken != null) builder.addHeader("Authorization", "Bearer " + currentToken);
                Request request = builder.get().build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        UserProfile[] arr = gson.fromJson(body, UserProfile[].class);
                        java.util.List<UserProfile> list = new java.util.ArrayList<>();
                        if (arr != null) for (UserProfile u : arr) if (u != null) list.add(u);
                        return list;
                    } else {
                        throw new IOException("获取用户列表失败: " + response.message());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("获取用户列表请求失败", e);
            }
        });
    }
}