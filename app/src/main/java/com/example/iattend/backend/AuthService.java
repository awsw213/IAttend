package com.example.iattend.backend;

import com.example.iattend.backend.utils.LogUtils;
import com.example.iattend.data.remote.SupabaseClient;
import com.example.iattend.data.remote.model.UserProfile;
import com.example.iattend.domain.model.AuthResult;
import com.example.iattend.domain.model.User;

import java.util.concurrent.CompletableFuture;

/**
 * 认证服务类
 * 专门为前端提供的后端API接口
 *
 * 使用说明：
 * 1. 所有方法都返回CompletableFuture，支持异步调用
 * 2. 调用成功返回AuthResult对象，包含用户信息和操作结果
 * 3. 调用失败返回包含错误信息的AuthResult对象
 * 4. 使用前请确保已正确配置SupabaseConfig中的API密钥
 */
public class AuthService {

    private final SupabaseClient supabaseClient;

    public AuthService() {
        this.supabaseClient = SupabaseClient.getInstance();
    }

    /**
     * 用户注册
     *
     * @param email 用户邮箱
     * @param password 用户密码（至少6位，包含字母和数字）
     * @param name 用户姓名
     * @return CompletableFuture<AuthResult> 注册结果
     *
     * 使用示例：
     * AuthService authService = new AuthService();
     * authService.signUp("user@example.com", "password123", "张三")
     *     .thenAccept(result -> {
     *         if (result.isSuccess()) {
     *             User user = result.getUser();
     *             String token = result.getToken();
     *             // 注册成功，处理用户信息和token
     *         } else {
     *             String error = result.getMessage();
     *             // 注册失败，显示错误信息
     *         }
     *     });
     */
    public CompletableFuture<AuthResult> signUp(String email, String password, String name) {
        LogUtils.methodEnter("AuthService", "signUp", email, "******", name);
        LogUtils.apiCall("AuthService", "用户注册", email, name);

        return supabaseClient.signUp(email, password, name)
                .thenApply(authResponse -> {
                    try {
                        User user = convertToUser(authResponse.getUser());
                        LogUtils.apiResponse("AuthService", "用户注册", true, "用户ID: " + user.getId());
                        LogUtils.methodExit("AuthService", "signUp", "注册成功");
                        return AuthResult.success(user, authResponse.getAccessToken());
                    } catch (Exception e) {
                        LogUtils.e("AuthService", "用户信息转换失败", e);
                        return AuthResult.error("用户信息转换失败: " + e.getMessage());
                    }
                })
                .exceptionally(throwable -> {
                    String errorMessage = extractErrorMessage(throwable);
                    LogUtils.apiResponse("AuthService", "用户注册", false, errorMessage);
                    LogUtils.e("AuthService", "注册失败", throwable);
                    LogUtils.methodExit("AuthService", "signUp", "注册失败");
                    return AuthResult.error(errorMessage);
                });
    }

    /**
     * 用户登录
     *
     * @param email 用户邮箱
     * @param password 用户密码
     * @return CompletableFuture<AuthResult> 登录结果
     *
     * 使用示例：
     * AuthService authService = new AuthService();
     * authService.signIn("user@example.com", "password123")
     *     .thenAccept(result -> {
     *         if (result.isSuccess()) {
     *             User user = result.getUser();
     *             String token = result.getToken();
     *             // 登录成功，保存用户信息和token
     *         } else {
     *             String error = result.getMessage();
     *             // 登录失败，显示错误信息
     *         }
     *     });
     */
    public CompletableFuture<AuthResult> signIn(String email, String password) {
        return supabaseClient.signIn(email, password)
                .thenApply(authResponse -> {
                    try {
                        User user = convertToUser(authResponse.getUser());
                        return AuthResult.success(user, authResponse.getAccessToken());
                    } catch (Exception e) {
                        return AuthResult.error("用户信息转换失败: " + e.getMessage());
                    }
                })
                .exceptionally(throwable -> {
                    String errorMessage = extractErrorMessage(throwable);
                    return AuthResult.error(errorMessage);
                });
    }

    /**
     * 获取当前登录用户信息
     *
     * @return CompletableFuture<User> 用户信息，如果未登录返回null
     *
     * 使用示例：
     * AuthService authService = new AuthService();
     * authService.getCurrentUser()
     *     .thenAccept(user -> {
     *         if (user != null) {
     *             // 用户已登录，显示用户信息
     *         } else {
     *             // 用户未登录，跳转到登录界面
     *         }
     *     });
     */
    public CompletableFuture<User> getCurrentUser() {
        if (!supabaseClient.isUserLoggedIn()) {
            return CompletableFuture.completedFuture(null);
        }

        return supabaseClient.getCurrentUserProfile()
                .thenApply(this::convertToUser)
                .exceptionally(throwable -> {
                    // 如果获取用户档案失败，尝试从认证信息中获取基本信息
                    if (supabaseClient.getCurrentUser() != null) {
                        return convertToUser(supabaseClient.getCurrentUser());
                    }
                    return null;
                });
    }

    /**
     * 更新用户信息
     *
     * @param user 要更新的用户信息
     * @return CompletableFuture<AuthResult> 更新结果
     *
     * 使用示例：
     * AuthService authService = new AuthService();
     * User user = new User();
     * user.setId("user_id");
     * user.setName("新姓名");
     * user.setUserClass("新班级");
     * authService.updateUser(user)
     *     .thenAccept(result -> {
     *         if (result.isSuccess()) {
     *             // 更新成功
     *         } else {
     *             // 更新失败，显示错误信息
     *         }
     *     });
     */
    public CompletableFuture<AuthResult> updateUser(User user) {
        if (!supabaseClient.isUserLoggedIn()) {
            return CompletableFuture.completedFuture(AuthResult.error("用户未登录"));
        }

        UserProfile userProfile = convertToUserProfile(user);
        return supabaseClient.upsertUserProfile(userProfile)
                .thenApply(profile -> {
                    User updatedUser = convertToUser(profile);
                    return AuthResult.success(updatedUser, supabaseClient.getCurrentToken());
                })
                .exceptionally(throwable -> {
                    String errorMessage = extractErrorMessage(throwable);
                    return AuthResult.error(errorMessage);
                });
    }

    /**
     * 用户登出
     *
     * @return CompletableFuture<Void> 登出结果
     *
     * 使用示例：
     * AuthService authService = new AuthService();
     * authService.signOut()
     *     .thenAccept(result -> {
     *         // 登出成功，清除本地数据，跳转到登录界面
     *     });
     */
    public CompletableFuture<Void> signOut() {
        return CompletableFuture.runAsync(() -> {
            supabaseClient.signOut();
        });
    }

    /**
     * 检查用户是否已登录
     *
     * @return true如果用户已登录，否则返回false
     */
    public boolean isUserLoggedIn() {
        return supabaseClient.isUserLoggedIn();
    }

    // ========== 私有辅助方法 ==========

    /**
     * 将Supabase用户模型转换为领域用户模型
     */
    private User convertToUser(com.example.iattend.data.remote.model.AuthResponse.User supabaseUser) {
        User user = new User();
        user.setId(supabaseUser.getId());
        user.setEmail(supabaseUser.getEmail());
        user.setEmailVerified(supabaseUser.getEmail_confirmed_at() != null);

        if (supabaseUser.getUser_metadata() != null) {
            user.setName(supabaseUser.getUser_metadata().getName());
        }

        return user;
    }

    /**
     * 将Supabase用户档案模型转换为领域用户模型
     */
    private User convertToUser(UserProfile profile) {
        User user = new User();
        user.setId(profile.getUserId());
        user.setName(profile.getName());
        user.setEmail(profile.getEmail());
        user.setUserClass(profile.getUserClass());
        user.setAvatarUrl(profile.getAvatarUrl());

        return user;
    }

    /**
     * 将领域用户模型转换为Supabase用户档案模型
     */
    private UserProfile convertToUserProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setName(user.getName());
        profile.setEmail(user.getEmail());
        profile.setUserClass(user.getUserClass());
        profile.setAvatarUrl(user.getAvatarUrl());

        return profile;
    }

    /**
     * 提取异常信息的核心内容
     */
    private String extractErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }

        String message = throwable.getMessage();
        if (message == null) {
            message = "操作失败";
        }

        Throwable cause = throwable.getCause();
        String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
        String combined = (message + " " + causeMsg).toLowerCase();

        if (combined.contains("user already registered")) {
            return "该邮箱已被注册";
        } else if (combined.contains("invalid login credentials")) {
            return "邮箱或密码错误";
        } else if (combined.contains("email not confirmed")) {
            return "邮箱尚未验证";
        } else if (combined.contains("password should be at least")) {
            return "密码长度不足";
        } else if (combined.contains("invalid email")) {
            return "邮箱格式不正确";
        } else if (combined.contains("timeout") || combined.contains("failed to connect") || combined.contains("unable to resolve host") || combined.contains("ssl") || combined.contains("handshake") || message.contains("网络")) {
            return "网络连接失败，请检查网络设置";
        } else {
            int colonIndex = message.indexOf(':');
            if (colonIndex > 0 && colonIndex < message.length() - 2) {
                return message.substring(colonIndex + 1).trim();
            }
            return message;
        }
    }
}