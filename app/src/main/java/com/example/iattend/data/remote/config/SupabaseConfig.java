package com.example.iattend.data.remote.config;

/**
 * Supabase配置类
 * 集中管理Supabase连接参数
 */
public class SupabaseConfig {
    // Supabase项目URL
    public static final String SUPABASE_URL = "https://ojbloiozgxsfskbgseoc.supabase.co";

    // Supabase服务角色密钥（生产环境应使用更安全的方式管理）
    public static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9qYmxvaW96Z3hzZnNrYmdzZW9jIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI3NzkzMTksImV4cCI6MjA3ODM1NTMxOX0.iHubNLQiNY-oK6BsaZ8E_8-7A2UzkYDi8gOHpdrZypQ";

    // Supabase API URL前缀
    public static final String AUTH_BASE_URL = SUPABASE_URL + "/auth/v1";
    public static final String REST_BASE_URL = SUPABASE_URL + "/rest/v1";
    public static final String STORAGE_BASE_URL = SUPABASE_URL + "/storage/v1";

    // 表名
    public static final String PROFILES_TABLE = "profiles";
    public static final String FEEDBACKS_TABLE = "feedbacks";

    // 认证端点
    public static final String SIGNUP_ENDPOINT = "/signup";
    public static final String LOGIN_ENDPOINT = "/token?grant_type=password";
    public static final String USER_ENDPOINT = "/user";

    // Storage 桶名
    public static final String AVATAR_BUCKET = "avatars";
    public static final String FEEDBACK_IMAGE_BUCKET = "feedback-images";


    private SupabaseConfig() {
        // 私有构造函数，防止实例化
    }
}