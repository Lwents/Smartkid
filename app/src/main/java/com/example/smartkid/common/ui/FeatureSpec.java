package com.example.smartkid.common.ui;

import com.example.smartkid.common.navigation.UserRole;

/**
 * Mô tả một chức năng quản lý gồm tiêu đề, endpoint, loại hành động và trạng thái khả dụng.
 * Role sở hữu được khai báo rõ qua {@link #owner()} để danh bạ Teacher và Admin độc lập, không
 * màn hình nào phải suy role bằng tiền tố chuỗi.
 */
public final class FeatureSpec {
    private final String key;
    private final String title;
    private final String endpoint;
    private final String actionKind;
    private final String unavailableReason;
    private final UserRole owner;

    public FeatureSpec(String key, String title, String endpoint, String actionKind,
                       String unavailableReason, UserRole owner) {
        this.key = key;
        this.title = title;
        this.endpoint = endpoint;
        this.actionKind = actionKind;
        this.unavailableReason = unavailableReason == null ? "" : unavailableReason;
        this.owner = owner == null ? UserRole.UNKNOWN : owner;
    }

    public String getKey() { return key; }
    public String getTitle() { return title; }
    public String getEndpoint() { return endpoint; }
    public String getActionKind() { return actionKind; }
    public String getUnavailableReason() { return unavailableReason; }
    public UserRole owner() { return owner; }
    /** Chức năng có dùng được không; nếu không thì unavailableReason là lý do hiển thị cho người dùng. */
    public boolean isAvailable() { return unavailableReason.isEmpty(); }

    /** Vai trò này có quyền mở chức năng không. */
    public boolean isAllowedForRole(UserRole role) {
        return role != null && role == owner;
    }

    /** Phiên bản nhận chuỗi vai trò thô từ server. */
    public boolean isAllowedForRole(String role) {
        return isAllowedForRole(UserRole.fromString(role));
    }
}
