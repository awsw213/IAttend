package com.example.iattend.data.remote.model;

import com.google.gson.annotations.SerializedName;

/**
 * 用户档案数据模型
 * 对应Supabase中的profiles表结构
 */
public class UserProfile {
    @SerializedName("user_id")
    private String userId;

    @SerializedName("name")
    private String name;

    @SerializedName("email")
    private String email;

    @SerializedName("class")
    private String userClass;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("updated_at")
    private String updatedAt;

    // 构造函数
    public UserProfile() {}

    public UserProfile(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    // Getter和Setter方法
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserClass() {
        return userClass;
    }

    public void setUserClass(String userClass) {
        this.userClass = userClass;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", userClass='" + userClass + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}