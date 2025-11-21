package com.example.iattend.backend;

import com.example.iattend.data.remote.SupabaseClient;
import com.example.iattend.data.remote.SupabaseStorageClient;
import com.example.iattend.data.remote.config.SupabaseConfig;
import com.example.iattend.data.remote.model.Feedback;
import com.google.gson.Gson;

import okhttp3.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 反馈服务类
 * 处理反馈提交，支持图片上传
 *
 * 工作流程：
 * 1. 如果有图片 → 上传到 Storage → 获取 public URL
 * 2. 准备反馈数据（content + image_url）
 * 3. 使用 PostgREST 插入到 feedbacks 表
 * 4. RLS 使用 auth.uid() 验证
 *
 * RLS 策略：
 * - INSERT: auth.uid() = user_id
 * - SELECT: auth.uid() = user_id
 *
 * 使用示例：
 * FeedbackService service = new FeedbackService();
 * service.submitWithImage(userId, type, content, imageData)
 *     .thenAccept(success -> {
 *         if (success) {
 *             // 提交成功
 *         }
 *     });
 */
public class FeedbackService {
    private final SupabaseStorageClient storageClient;
    private final SupabaseClient supabaseClient;
    private final Gson gson;
    private final OkHttpClient httpClient;

    /**
     * 构造函数
     * 初始化各个依赖组件
     */
    public FeedbackService() {
        this.storageClient = SupabaseStorageClient.getInstance();
        this.supabaseClient = SupabaseClient.getInstance();
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    }

    /**
     * 上传反馈截图到 Storage
     *
     * 上传路径：{userId}/feedback_{timestamp}.jpg
     * 使用 JPEG 格式，保证兼容性
     *
     * @param userId 用户ID（用于组织文件夹）
     * @param imageData 图片二进制数据
     * @return CompletableFuture<String> 图片的 public URL
     */
    private CompletableFuture<String> uploadFeedbackImage(String userId, byte[] imageData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 生成唯一文件名：feedback_20251119_120000.jpg
                String timeStamp = new SimpleDateFormat(
                    "yyyyMMdd_HHmmss", Locale.getDefault()
                ).format(new Date());

                String fileName = "feedback_" + timeStamp + ".jpg";
                String filePath = userId + "/" + fileName;

                // 上传到 feedback-images 桶
                return storageClient.uploadFile(
                    SupabaseConfig.FEEDBACK_IMAGE_BUCKET,
                    filePath,
                    imageData,
                    "image/jpeg"
                ).join();

            } catch (Exception e) {
                throw new RuntimeException("反馈图片上传失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 提交反馈到数据库（不含图片）
     *
     * @param userId 用户ID（外键）
     * @param type 反馈类型
     * @param content 反馈内容
     * @return CompletableFuture<Boolean> 成功返回 true
     */
    public CompletableFuture<Boolean> submitFeedback(
            String userId, String type, String content) {
        return submitFeedbackInternal(userId, type, content, null);
    }

    /**
     * 提交反馈到数据库（包含图片）
     *
     * 完整工作流程：
     * 1. 上传图片到 Storage（如果有）
     * 2. 创建 Feedback 对象（包含 image_url）
     * 3. 获取用户 token（用于 RLS）
     * 4. 发送到 PostgREST API
     * 5. 验证响应
     *
     * @param userId 用户ID
     * @param type 反馈类型
     * @param content 反馈内容
     * @param imageData 图片二进制数据（可选，null 表示无图）
     * @return CompletableFuture<Boolean> 成功返回 true
     */
    public CompletableFuture<Boolean> submitFeedbackWithImage(
            String userId, String type, String content, byte[] imageData) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String imageUrl = null;

                // 1. 如果有图片，先上传到 Storage
                if (imageData != null && imageData.length > 0) {
                    imageUrl = uploadFeedbackImage(userId, imageData).join();
                }

                // 2. 创建反馈对象
                //    - 有图片：imageUrl 会被设置
                //    - 无图片：imageUrl 为 null
                Feedback feedback = new Feedback(userId, type, content, imageUrl);
                String jsonBody = gson.toJson(feedback);

                // 3. 获取当前用户的 token（RLS 验证需要）
                String token = supabaseClient.getCurrentToken();
                if (token == null) {
                    throw new RuntimeException("错误：用户未登录，无法提交反馈");
                }

                // 4. 创建 HTTP 请求
                RequestBody body = RequestBody.create(
                    jsonBody,
                    MediaType.get("application/json")
                );

                // PostgREST API: /rest/v1/{table}
                Request request = new Request.Builder()
                    .url(SupabaseConfig.REST_BASE_URL + "/" + SupabaseConfig.FEEDBACKS_TABLE)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)           // ANON KEY
                    .addHeader("Authorization", "Bearer " + token)               // USER TOKEN（RLS 需要）
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")               // 返回插入的数据
                    .post(body)
                    .build();

                // 5. 执行请求
                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body() != null ?
                    response.body().string() : "";

                // 6. 验证响应
                if (response.isSuccessful()) {
                    // 可选：解析返回的反馈数据
                    Feedback createdFeedback = gson.fromJson(responseBody, Feedback.class);
                    return true;
                } else {
                    // 失败，抛出详细错误
                    throw new IOException(
                        "反馈提交失败: HTTP " + response.code() +
                        " - " + response.message() +
                        "\nResponse: " + responseBody
                    );
                }

            } catch (Exception e) {
                throw new RuntimeException("提交反馈失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 内部通用方法：提交反馈（自动判断是否有图片）
     *
     * @param userId 用户ID
     * @param type 反馈类型
     * @param content 反馈内容
     * @param imageData 图片数据（可为 null）
     * @return CompletableFuture<Boolean>
     */
    private CompletableFuture<Boolean> submitFeedbackInternal(
            String userId, String type, String content, byte[] imageData) {

        if (imageData != null && imageData.length > 0) {
            return submitFeedbackWithImage(userId, type, content, imageData);
        } else {
            return submitFeedbackWithImage(userId, type, content, null);
        }
    }

    /**
     * 简化方法：提交反馈（自动处理图片逻辑）
     *
     * @param userId 用户ID
     * @param type 反馈类型
     * @param content 反馈内容
     * @param imageData 图片数据（可为 null）
     * @return CompletableFuture<Boolean>
     */
    public CompletableFuture<Boolean> submit(
            String userId, String type, String content, byte[] imageData) {
        return submitFeedbackInternal(userId, type, content, imageData);
    }
}
