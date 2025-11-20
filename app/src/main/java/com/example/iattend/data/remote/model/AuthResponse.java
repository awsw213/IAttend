package com.example.iattend.data.remote.model;

import com.google.gson.annotations.SerializedName;

/**
 * 认证响应数据模型
 */
public class AuthResponse {
    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("expires_in")
    private long expiresIn;

    @SerializedName("user")
    private User user;

    @SerializedName("access_token_expires_at")
    private long accessTokenExpiresAt;

    @SerializedName("refresh_token_expires_at")
    private long refreshTokenExpiresAt;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public long getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(long accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public long getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public void setRefreshTokenExpiresAt(long refreshTokenExpiresAt) {
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }

    /**
     * 用户信息
     */
    public static class User {
        private String id;
        private String email;
        private String email_confirmed_at;
        private String phone;
        private String phone_confirmed_at;
        private String last_sign_in_at;
        private String created_at;
        private String updated_at;
        private UserMetadata user_metadata;

        // Getters and Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getEmail_confirmed_at() {
            return email_confirmed_at;
        }

        public void setEmail_confirmed_at(String email_confirmed_at) {
            this.email_confirmed_at = email_confirmed_at;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getPhone_confirmed_at() {
            return phone_confirmed_at;
        }

        public void setPhone_confirmed_at(String phone_confirmed_at) {
            this.phone_confirmed_at = phone_confirmed_at;
        }

        public String getLast_sign_in_at() {
            return last_sign_in_at;
        }

        public void setLast_sign_in_at(String last_sign_in_at) {
            this.last_sign_in_at = last_sign_in_at;
        }

        public String getCreated_at() {
            return created_at;
        }

        public void setCreated_at(String created_at) {
            this.created_at = created_at;
        }

        public String getUpdated_at() {
            return updated_at;
        }

        public void setUpdated_at(String updated_at) {
            this.updated_at = updated_at;
        }

        public UserMetadata getUser_metadata() {
            return user_metadata;
        }

        public void setUser_metadata(UserMetadata user_metadata) {
            this.user_metadata = user_metadata;
        }

        public static class UserMetadata {
            private String name;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }
    }
}