package com.example.demo.dto;

public class JwtAuthResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private String tenantId = "default";
    private String role = "ROLE_USER";

    public JwtAuthResponse() {}

    public JwtAuthResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public JwtAuthResponse(String accessToken, String tenantId, String role) {
        this.accessToken = accessToken;
        this.tenantId = tenantId;
        this.role = role;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}