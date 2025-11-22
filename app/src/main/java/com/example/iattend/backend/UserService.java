package com.example.iattend.backend;

import com.example.iattend.data.remote.SupabaseClient;
import com.example.iattend.data.remote.SupabaseStorageClient;
import com.example.iattend.data.remote.config.SupabaseConfig;
import com.example.iattend.domain.model.User;

import java.util.concurrent.CompletableFuture;

/**
 * 用户服务类
 * 处理用户相关的业务逻辑，如头像上传、用户资料管理
 *
 * 功能：
 * 1. 上传用户头像到 Storage
 * 2. 更新用户的头像 URL
 * 3. 管理用户资料相关业务
 *
 * 使用示例：
 * UserService userService = new UserService();
 * userService.uploadAvatar("user_id", "avatar.jpg", imageData, userToken)
 *     .thenAccept(avatarUrl -> {
 *         // 上传成功，URL 可直接使用
 *     })
 *     .exceptionally(error -> {
 *         // 处理错误
 *         return null;
 *     });
 */
public class UserService {
    private final SupabaseStorageClient storageClient;

    /**
     * 构造函数
     * 初始化 SupabaseStorageClient
     */
    public UserService() {
        // 使用 SupabaseStorageClient 实例
        this.storageClient = SupabaseStorageClient.getInstance();
    }

    /**
     * 上传用户头像
     *
     * 工作流程：
     * 1. 构造文件路径：{userId}/avatar.jpg (固定文件名)
     * 2. 使用用户 token 上传到 avatars 桶
     * 3. 返回 public URL
     *
     * @param userId 用户ID（用于组织文件夹结构）
     * @param imageData 图片二进制数据（JPEG 格式，质量80%）
     * @param userToken 用户的 session token（用于 Storage 认证）
     * @return CompletableFuture<String> 头像的 public URL
     */
    public CompletableFuture<String> uploadAvatar(String userId, byte[] imageData, String userToken) {
        // 构造固定的文件路径: userId/avatar.jpg
        // 符合 SQL 策略: auth.uid()::text = (storage.foldername(name))[1]
        String filePath = userId + "/avatar.jpg";

        // 上传到 avatars 桶
        return storageClient.uploadFile(
            SupabaseConfig.AVATAR_BUCKET,
            filePath,
            imageData,
            userToken
        );
    }
}
