package com.yapi.plugin.config;

import java.util.Objects;

public class YApiServerConfig {
    private String name;
    private String url;
    private String token;
    private String projectId;
    private boolean isDefault;

    public YApiServerConfig() {
    }

    public YApiServerConfig(String name, String url, String token, String projectId) {
        this.name = name;
        this.url = url;
        this.token = token;
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * 获取 YApi 服务端基础 URL（去除尾部斜杠）。
     * 用户配置的 URL 原样使用，不追加 /yapi 路径。
     */
    public String getBaseUrl() {
        if (url == null) return "";
        String u = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        if (u.endsWith("/yapi")) return u;
        return u;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YApiServerConfig that = (YApiServerConfig) o;
        return Objects.equals(name, that.name) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url);
    }

    @Override
    public String toString() {
        return name + " (" + url + ")";
    }
}
