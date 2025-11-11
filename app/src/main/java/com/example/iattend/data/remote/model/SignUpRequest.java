package com.example.iattend.data.remote.model;

import com.google.gson.annotations.SerializedName;

/**
 * 注册请求数据模型
 */
public class SignUpRequest {
    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("data")
    private UserData userData;

    public SignUpRequest(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.userData = new UserData(name);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserData getUserData() {
        return userData;
    }

    public void setUserData(UserData userData) {
        this.userData = userData;
    }

    /**
     * 用户额外数据
     */
    public static class UserData {
        @SerializedName("name")
        private String name;

        public UserData(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}