package com.example.iattend.data.remote;

import com.example.iattend.data.remote.config.SupabaseConfig;
import com.google.gson.Gson;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Supabase Storage 客户端
 * 负责文件上传和管理
 *
 * 功能：
 * 1. 上传文件到 Supabase Storage
 * 2. 获取 public URL
 * 3. 支持断点续传（未来扩展）
 *
 * 使用示例：
 * SupabaseStorageClient storage = SupabaseStorageClient.getInstance();
 * storage.uploadFile("avatars", "user123/avatar.jpg", imageData, "image/jpeg")
 *     .thenAccept(url -> Log.d("Upload", "URL: " + url))
 *     .exceptionally(error -> { Log.e("Upload", "Error", error); return null; });
 */
public class SupabaseStorageClient {
    private static SupabaseStorageClient instance;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * 私有构造函数 - 单例模式
     * 配置 HTTP 客户端：
     * - 连接超时：60秒（文件上传需要较长时间）
     * - 读取超时：60秒
     * - 写入超时：60秒
     */
    private SupabaseStorageClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        this.gson = new Gson();
    }

    /**
     * 获取单例实例 - 线程安全
     */
    public static synchronized SupabaseStorageClient getInstance() {
        if (instance == null) {
            instance = new SupabaseStorageClient();
        }
        return instance;
    }

    /**
     * 上传文件到 Storage
     *
     * 工作流程：
     * 1. 构造上传 URL: /storage/v1/object/{bucket}/{filePath}
     * 2. 使用 PUT 方法上传
     * 3. 如果成功，返回 public URL
     *
     * @param bucketName 存储桶名称（avatars / feedback-images）
     * @param filePath 文件路径（例如：userId/filename.jpg）
     * @param fileData 文件二进制数据
     * @param contentType 内容类型（image/jpeg, image/png, etc.）
     * @return CompletableFuture<String> 返回文件的 public URL
     */
    public CompletableFuture<String> uploadFile(
            String bucketName, String filePath, byte[] fileData, String contentType) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 构造上传 URL
                String uploadUrl = SupabaseConfig.STORAGE_BASE_URL +
                    "/object/" + bucketName + "/" + filePath;

                // 2. 创建请求体
                RequestBody requestBody = RequestBody.create(
                    fileData,
                    MediaType.parse(contentType)
                );

                // 3. 创建上传请求
                Request uploadRequest = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)          // ANON KEY
                    .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_KEY) // 使用 ANON KEY 作为 Bearer
                    .put(requestBody) // 使用 PUT 上传文件
                    .build();

                // 4. 执行上传
                Response uploadResponse = httpClient.newCall(uploadRequest).execute();

                if (!uploadResponse.isSuccessful()) {
                    String errorBody = uploadResponse.body() != null ?
                        uploadResponse.body().string() : "Unknown error";
                    throw new IOException("Upload failed: " + uploadResponse.code() +
                        " - " + uploadResponse.message() + "\n" + errorBody);
                }

                // 5. 上传成功，返回 public URL
                return getPublicUrl(bucketName, filePath);

            } catch (Exception e) {
                throw new RuntimeException("File upload failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 获取文件的 public URL
     *
     * Supabase Storage 的 public URL 格式：
     * {SUPABASE_URL}/storage/v1/object/public/{bucket}/{filePath}
     *
     * @param bucketName 存储桶名称
     * @param filePath 文件路径
     * @return public URL
     */
    private String getPublicUrl(String bucketName, String filePath) {
        return SupabaseConfig.SUPABASE_URL +
               "/storage/v1/object/public/" +
               bucketName + "/" + filePath;
    }
}
