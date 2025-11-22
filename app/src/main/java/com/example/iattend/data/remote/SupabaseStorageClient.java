package com.example.iattend.data.remote;

import com.example.iattend.data.remote.config.SupabaseConfig;
import com.example.iattend.backend.utils.LogUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Supabase Storage 客户端
 * 使用 OkHttp 实现文件上传，支持用户认证
 *
 * 修复版本：
 * 1. 使用用户 session token 进行认证
 * 2. 支持图片压缩 (JPEG 格式，质量80%)
 * 3. 正确的 RLS 策略支持 (userId/avatar.jpg)
 */
public class SupabaseStorageClient {

    private static final String TAG = "SupabaseStorageClient";
    private static final MediaType JPEG_MEDIA_TYPE = MediaType.parse("image/jpeg");

    private static volatile SupabaseStorageClient instance;
    private final OkHttpClient httpClient;

    private SupabaseStorageClient() {
        this.httpClient = new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor())
                .build();
    }

    public static SupabaseStorageClient getInstance() {
        if (instance == null) {
            synchronized (SupabaseStorageClient.class) {
                if (instance == null) {
                    instance = new SupabaseStorageClient();
                }
            }
        }
        return instance;
    }

    /**
     * 上传文件到 Storage (Java 兼容版本)
     *
     * @param bucketName 存储桶名称 (如: "avatars")
     * @param filePath 文件路径 (如: "userId/avatar.jpg")
     * @param fileData 文件字节数组 (JPEG格式)
     * @param userToken 用户的 session token (必需，用于RLS认证)
     * @return 文件的 public URL
     */
    public CompletableFuture<String> uploadFile(
            String bucketName,
            String filePath,
            byte[] fileData,
            String userToken
    ) {
        LogUtils.d(TAG, "Uploading file: " + filePath + " to bucket: " + bucketName);

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (userToken == null || userToken.isEmpty()) {
                    throw new RuntimeException("User token is required for upload");
                }

                // 构造 Storage API URL
                String uploadUrl = String.format("%s/storage/v1/object/%s/%s",
                        SupabaseConfig.SUPABASE_URL,
                        bucketName,
                        filePath);

                LogUtils.d(TAG, "Upload URL: " + uploadUrl);

                // 创建请求体 (JPEG数据)
                RequestBody requestBody = RequestBody.create(fileData, JPEG_MEDIA_TYPE);

                // 创建请求 (使用Bearer token认证)
                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .addHeader("Authorization", "Bearer " + userToken)
                        .addHeader("Content-Type", "image/jpeg")
                        .put(requestBody)
                        .build();

                // 执行请求
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    LogUtils.d(TAG, "Upload response code: " + response.code());
                    LogUtils.d(TAG, "Upload response body: " + responseBody);

                    if (!response.isSuccessful()) {
                        throw new IOException("Upload failed with code " + response.code() + ": " + responseBody);
                    }

                    // 构造 public URL
                    String publicUrl = String.format("%s/storage/v1/object/public/%s/%s",
                            SupabaseConfig.SUPABASE_URL,
                            bucketName,
                            filePath);

                    LogUtils.d(TAG, "Public URL: " + publicUrl);
                    LogUtils.i(TAG, "File uploaded successfully");
                    return publicUrl;

                }
            } catch (Exception e) {
                LogUtils.e(TAG, "File upload failed", e);
                throw new RuntimeException("File upload failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 获取文件的 public URL
     *
     * @param bucketName 存储桶名称
     * @param filePath 文件路径
     * @return 文件的 public URL
     */
    public CompletableFuture<String> getPublicUrl(
            String bucketName,
            String filePath
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String publicUrl = String.format("%s/storage/v1/object/public/%s/%s",
                        SupabaseConfig.SUPABASE_URL,
                        bucketName,
                        filePath);

                LogUtils.d(TAG, "Public URL: " + publicUrl);
                return publicUrl;

            } catch (Exception e) {
                LogUtils.e(TAG, "Failed to get public URL", e);
                throw new RuntimeException("Failed to get public URL: " + e.getMessage(), e);
            }
        });
    }
}