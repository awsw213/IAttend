package com.example.iattend.domain.model;

/**
 * 认证结果模型
 * 用于封装注册/登录操作的结果
 */
public class AuthResult {
    private boolean success;
    private String message;
    private User user;
    private String token;

    public AuthResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public AuthResult(boolean success, String message, User user, String token) {
        this.success = success;
        this.message = message;
        this.user = user;
        this.token = token;
    }

    public static AuthResult success(User user, String token) {
        return new AuthResult(true, "操作成功", user, token);
    }

    public static AuthResult error(String message) {
        return new AuthResult(false, message);
    }

    // Getter和Setter方法
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return "AuthResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", user=" + user +
                ", token='" + token + '\'' +
                '}';
    }
}