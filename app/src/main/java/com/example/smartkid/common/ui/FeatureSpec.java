package com.example.smartkid.common.ui;

import com.example.smartkid.common.navigation.UserRole;

/**
 * Neutral value type describing a single management feature (title, backing endpoint, optional
 * action kind and availability). Role ownership is explicit via {@link #owner()} so the two
 * role registries ({@code TeacherManagementSpec}, {@code AdminManagementSpec}) stay independent
 * and no screen resolves both roles through String prefixes.
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
    public boolean isAvailable() { return unavailableReason.isEmpty(); }

    public boolean isAllowedForRole(UserRole role) {
        return role != null && role == owner;
    }

    public boolean isAllowedForRole(String role) {
        return isAllowedForRole(UserRole.fromString(role));
    }
}
