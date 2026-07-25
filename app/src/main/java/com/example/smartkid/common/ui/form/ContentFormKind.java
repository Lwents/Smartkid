package com.example.smartkid.common.ui.form;

import com.example.smartkid.common.navigation.UserRole;

/**
 * Typed replacement for the former raw {@code kind} String that drove the shared create form.
 * Each value is owned by exactly one role, so a screen can no longer serve both Admin and
 * Teacher through String branching.
 */
public enum ContentFormKind {
    ADMIN_USER(UserRole.ADMIN),
    TEACHER_COURSE(UserRole.TEACHER),
    TEACHER_MODULE(UserRole.TEACHER),
    TEACHER_LESSON(UserRole.TEACHER),
    TEACHER_EXERCISE(UserRole.TEACHER),
    TEACHER_EXAM(UserRole.TEACHER);

    private final UserRole owner;

    ContentFormKind(UserRole owner) {
        this.owner = owner;
    }

    public UserRole owner() {
        return owner;
    }

    public boolean isAllowedFor(UserRole role) {
        return role == owner;
    }
}
