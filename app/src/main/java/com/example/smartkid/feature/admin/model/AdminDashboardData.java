package com.example.smartkid.feature.admin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable data rendered by the native admin dashboard. */
public final class AdminDashboardData {
    private final Kpis kpis;
    private final List<CourseItem> topCourses;
    private final ActiveUsers activeUsers;
    private final Security security;
    private final SystemHealth systemHealth;

    public AdminDashboardData(Kpis kpis, List<CourseItem> topCourses,
                              ActiveUsers activeUsers, Security security,
                              SystemHealth systemHealth) {
        this.kpis = kpis == null ? new Kpis(0, 0, 0, 0, 0, 0) : kpis;
        this.topCourses = immutable(topCourses);
        this.activeUsers = activeUsers == null ? new ActiveUsers(0, 10) : activeUsers;
        this.security = security == null ? new Security(0, 0, 0) : security;
        this.systemHealth = systemHealth == null
                ? new SystemHealth(0, 0, 0, "", "") : systemHealth;
    }

    public Kpis getKpis() { return kpis; }
    public List<CourseItem> getTopCourses() { return topCourses; }
    public ActiveUsers getActiveUsers() { return activeUsers; }
    public Security getSecurity() { return security; }
    public SystemHealth getSystemHealth() { return systemHealth; }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(values == null
                ? new ArrayList<>() : new ArrayList<>(values));
    }

    public static final class Kpis {
        private final int dailyActiveUsers;
        private final int signupsLastSevenDays;
        private final int students;
        private final int teachers;
        private final int courses;
        private final int lessons;

        public Kpis(int dailyActiveUsers, int signupsLastSevenDays, int students,
                    int teachers, int courses, int lessons) {
            this.dailyActiveUsers = dailyActiveUsers;
            this.signupsLastSevenDays = signupsLastSevenDays;
            this.students = students;
            this.teachers = teachers;
            this.courses = courses;
            this.lessons = lessons;
        }

        public int getDailyActiveUsers() { return dailyActiveUsers; }
        public int getSignupsLastSevenDays() { return signupsLastSevenDays; }
        public int getStudents() { return students; }
        public int getTeachers() { return teachers; }
        public int getCourses() { return courses; }
        public int getLessons() { return lessons; }
    }

    public static final class CourseItem {
        private final String id;
        private final String title;
        private final int enrollments;

        public CourseItem(String id, String title, int enrollments) {
            this.id = safe(id);
            this.title = safe(title);
            this.enrollments = enrollments;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public int getEnrollments() { return enrollments; }
    }

    public static final class ActiveUsers {
        private final int count;
        private final int windowMinutes;

        public ActiveUsers(int count, int windowMinutes) {
            this.count = count;
            this.windowMinutes = windowMinutes;
        }

        public int getCount() { return count; }
        public int getWindowMinutes() { return windowMinutes; }
    }

    public static final class Security {
        private final int failedLogins;
        private final int lockedAccounts;
        private final int sslDaysToExpire;

        public Security(int failedLogins, int lockedAccounts, int sslDaysToExpire) {
            this.failedLogins = failedLogins;
            this.lockedAccounts = lockedAccounts;
            this.sslDaysToExpire = sslDaysToExpire;
        }

        public int getFailedLogins() { return failedLogins; }
        public int getLockedAccounts() { return lockedAccounts; }
        public int getSslDaysToExpire() { return sslDaysToExpire; }
    }

    public static final class SystemHealth {
        private final int cpuPercent;
        private final int ramPercent;
        private final int diskPercent;
        private final String backupLastRun;
        private final String backupStatus;

        public SystemHealth(int cpuPercent, int ramPercent, int diskPercent,
                            String backupLastRun, String backupStatus) {
            this.cpuPercent = clamp(cpuPercent);
            this.ramPercent = clamp(ramPercent);
            this.diskPercent = clamp(diskPercent);
            this.backupLastRun = safe(backupLastRun);
            this.backupStatus = safe(backupStatus);
        }

        public int getCpuPercent() { return cpuPercent; }
        public int getRamPercent() { return ramPercent; }
        public int getDiskPercent() { return diskPercent; }
        public String getBackupLastRun() { return backupLastRun; }
        public String getBackupStatus() { return backupStatus; }

        private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    }

    public static final class ActivityPoint {
        private final String date;
        private final int newUsers;

        public ActivityPoint(String date, int newUsers) {
            this.date = safe(date);
            this.newUsers = Math.max(0, newUsers);
        }

        public String getDate() { return date; }
        public int getNewUsers() { return newUsers; }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
