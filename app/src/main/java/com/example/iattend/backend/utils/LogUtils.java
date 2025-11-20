package com.example.iattend.backend.utils;

import android.util.Log;

/**
 * 日志工具类
 * 统一管理应用日志输出
 */
public class LogUtils {

    private static final String TAG_PREFIX = "IAttend_";
    private static boolean DEBUG_ENABLED = true; // 生产环境应设置为false

    /**
     * 打印DEBUG级别日志
     */
    public static void d(String tag, String message) {
        if (DEBUG_ENABLED) {
            Log.d(TAG_PREFIX + tag, message);
        }
    }

    /**
     * 打印INFO级别日志
     */
    public static void i(String tag, String message) {
        Log.i(TAG_PREFIX + tag, message);
    }

    /**
     * 打印WARN级别日志
     */
    public static void w(String tag, String message) {
        Log.w(TAG_PREFIX + tag, message);
    }

    /**
     * 打印ERROR级别日志
     */
    public static void e(String tag, String message) {
        Log.e(TAG_PREFIX + tag, message);
    }

    /**
     * 打印ERROR级别日志，包含异常信息
     */
    public static void e(String tag, String message, Throwable throwable) {
        Log.e(TAG_PREFIX + tag, message, throwable);
    }

    /**
     * 打印方法调用日志
     */
    public static void methodEnter(String tag, String methodName, Object... params) {
        if (DEBUG_ENABLED) {
            StringBuilder sb = new StringBuilder();
            sb.append("进入方法: ").append(methodName);
            if (params.length > 0) {
                sb.append(", 参数: ");
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i]);
                }
            }
            d(tag, sb.toString());
        }
    }

    /**
     * 打印方法退出日志
     */
    public static void methodExit(String tag, String methodName, Object result) {
        if (DEBUG_ENABLED) {
            String message = result != null ?
                "退出方法: " + methodName + ", 返回: " + result :
                "退出方法: " + methodName;
            d(tag, message);
        }
    }

    /**
     * 打印API调用日志
     */
    public static void apiCall(String tag, String apiName, String... params) {
        StringBuilder sb = new StringBuilder();
        sb.append("API调用: ").append(apiName);
        if (params.length > 0) {
            sb.append(", 参数: ");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(params[i]);
            }
        }
        i(tag, sb.toString());
    }

    /**
     * 打印API响应日志
     */
    public static void apiResponse(String tag, String apiName, boolean success, String message) {
        String status = success ? "成功" : "失败";
        i(tag, String.format("API响应: %s - %s, 消息: %s", apiName, status, message));
    }

    /**
     * 设置是否启用DEBUG日志
     */
    public static void setDebugEnabled(boolean enabled) {
        DEBUG_ENABLED = enabled;
    }

    /**
     * 检查是否启用DEBUG日志
     */
    public static boolean isDebugEnabled() {
        return DEBUG_ENABLED;
    }
}