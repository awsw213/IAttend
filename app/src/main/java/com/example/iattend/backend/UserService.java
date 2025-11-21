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
 * userService.uploadAvatar("user_id", "avatar.jpg", imageData)
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
        this.storageClient = SupabaseStorageClient.getInstance();
    }

    /**
     * 上传用户头像
     *
     * 工作流程：
     * 1. 构造文件路径：{userId}/{filename}
     * 2. 上传到 avatars 桶
     * 3. 返回 public URL
     *
     * @param userId 用户ID（用于组织文件夹结构）
     * @param fileName 文件名（例如：avatar_20251119_120000.jpg）
     * @param imageData 图片二进制数据（JPEG 格式）
     * @return CompletableFuture<String> 头像的 public URL
     */
    public CompletableFuture<String> uploadAvatar(String userId, String fileName, byte[] imageData) {
        // 根据 userId 组织文件夹，避免文件名冲突
        String filePath = userId + "/" + fileName;

        // 上传到 avatars 桶
        return storageClient.uploadFile(
            SupabaseConfig.AVATAR_BUCKET,
            filePath,
            imageData,
            "image/jpeg"
        );
    }
}
