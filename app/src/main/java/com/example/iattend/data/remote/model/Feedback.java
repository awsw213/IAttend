package com.example.iattend.data.remote.model;

import com.google.gson.annotations.SerializedName;

/**
 * 反馈数据模型
 * 对应 Supabase 中的 feedbacks 表
 *
 * 表结构（已移除 type 字段以匹配数据库）：
 * - id: bigint (自增主键) → Long
 * - user_id: uuid (外键) → String
 * - content: text (反馈内容) → String
 * - image_url: text (可选图片) → String
 * - status: text (状态，默认 'pending') → String
 * - created_at: timestamp (创建时间) → String
 *
 * RLS 策略：
 * - 插入：auth.uid() = user_id
 * - 查询：auth.uid() = user_id
 */
public class Feedback {
    @SerializedName("id")
    private Long id; // bigint → Long

    @SerializedName("user_id")
    private String userId; // uuid → String

    @SerializedName("content")
    private String content; // text → String

    @SerializedName("image_url")
    private String imageUrl; // text (nullable) → String

    @SerializedName("status")
    private String status; // text → String (pending/in_progress/resolved)

    @SerializedName("created_at")
    private String createdAt; // timestamp → String

    /**
     * 默认构造函数
     * 初始化 status 为 'pending'
     */
    public Feedback() {
        this.status = "pending"; // 默认状态
    }

    /**
     * 构造函数：创建反馈（不含图片）
     *
     * @param userId 用户ID
     * @param content 反馈内容
     */
    public Feedback(String userId, String content) {
        this(); // 调用默认构造函数设置 status
        this.userId = userId;
        this.content = content;
    }

    /**
     * 构造函数：创建反馈（含图片）
     *
     * @param userId 用户ID
     * @param content 反馈内容
     * @param imageUrl 图片URL
     */
    public Feedback(String userId, String content, String imageUrl) {
        this(userId, content); // 复用上面的构造函数
        this.imageUrl = imageUrl;
    }

    // ==================== Getters ====================

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    // ==================== Setters ====================

    public void setId(Long id) {
        this.id = id;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断反馈是否包含图片
     *
     * @return true if 有图片，false otherwise
     */
    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isEmpty();
    }

    /**
     * 判断反馈是否已解决
     *
     * @return true if status is 'resolved'
     */
    public boolean isResolved() {
        return "resolved".equals(status);
    }

    /**
     * 获取摘要（用于日志和调试）
     */
    public String getSummary() {
        String contentSummary = (content != null && content.length() > 50)
            ? content.substring(0, 50) + "..."
            : content;

        return String.format(
            "Feedback{id=%s, userId='%s', hasImage=%s, status='%s'}",
            id, userId, hasImage(), status
        );
    }

    // ==================== toString ====================

    @Override
    public String toString() {
        return "Feedback{" +
            "id=" + id +
            ", userId='" + userId + '\'' +
            ", content='" + (content != null && content.length() > 100
                             ? content.substring(0, 100) + "..."
                             : content) + '\'' +
            ", hasImage=" + hasImage() +
            ", status='" + status + '\'' +
            ", createdAt='" + createdAt + '\'' +
            '}';
    }
}
