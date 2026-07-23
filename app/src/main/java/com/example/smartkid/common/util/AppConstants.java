package com.example.smartkid.common.util;

import com.example.smartkid.BuildConfig;

public final class AppConstants {
    public static final String API_BASE_URL = BuildConfig.API_BASE_URL;

    public static final String LOGIN_ENDPOINT = "account/login/";
    public static final String REGISTER_ENDPOINT = "account/register/";
    public static final String FORGOT_PASSWORD_ENDPOINT = "account/password/reset/";
    public static final String RESET_PASSWORD_ENDPOINT = "account/password/reset/confirm/";
    public static final String CHANGE_PASSWORD_ENDPOINT = "account/password/change/";
    public static final String REFRESH_ENDPOINT = "account/refresh/";
    public static final String LOGOUT_ENDPOINT = "account/logout/";
    public static final String DASHBOARD_ENDPOINT = "student/dashboard/";
    public static final String MY_COURSES_ENDPOINT = "student/courses/";
    public static final String PROFILE_ENDPOINT = "student/account/profile/";

    public static final String EXTRA_COURSE_ID = "extra_course_id";
    public static final String EXTRA_COURSE_TITLE = "extra_course_title";
    public static final String EXTRA_LESSON_ID = "extra_lesson_id";
    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    public static final String EXTRA_LESSON_TITLE = "extra_lesson_title";

    public static final int NETWORK_TIMEOUT_MS = 20_000;
    public static final int AI_NETWORK_TIMEOUT_MS = 90_000;

    private AppConstants() {
        // Lớp chỉ chứa hằng số.
    }
}
