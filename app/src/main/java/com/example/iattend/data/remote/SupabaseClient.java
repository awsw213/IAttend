package com.example.iattend.data.remote;

import com.example.iattend.data.remote.config.SupabaseConfig;
import com.example.iattend.data.remote.model.AuthResponse;
import com.example.iattend.data.remote.model.LoginRequest;
import com.example.iattend.data.remote.model.SignUpRequest;
import com.example.iattend.data.remote.model.UserProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
        this.httpClient = new OkHttpClient.Builder()
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
                        .addHeader("Content-Type", "application/json")
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
                        .addHeader("Content-Type", "application/json")
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
                throw new RuntimeException("登录请求失败", e);
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
}