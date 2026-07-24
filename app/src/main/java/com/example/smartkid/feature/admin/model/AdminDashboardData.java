package com.example.smartkid.feature.admin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable data rendered by the native admin dashboard. */
public final class AdminDashboardData {
    private final Kpis kpis;
    private final List<CourseItem> topCourses;
    private final List<TransactionItem> recentTransactions;
    private final ActiveUsers activeUsers;
    private final Security security;
    private final SystemHealth systemHealth;

    public AdminDashboardData(Kpis kpis, List<CourseItem> topCourses,
                              List<TransactionItem> recentTransactions,
                              ActiveUsers activeUsers, Security security,
                              SystemHealth systemHealth) {
        this.kpis = kpis == null ? new Kpis(0, 0, 0, 0, 0, 0) : kpis;
        this.topCourses = immutable(topCourses);
        this.recentTransactions = immutable(recentTransactions);
        this.activeUsers = activeUsers == null ? new ActiveUsers(0, 10) : activeUsers;
        this.security = security == null ? new Security(0, 0, 0) : security;
        this.systemHealth = systemHealth == null
                ? new SystemHealth(0, 0, 0, "", "") : systemHealth;
    }

    public Kpis getKpis() { return kpis; }
    public List<CourseItem> getTopCourses() { return topCourses; }
    public List<TransactionItem> getRecentTransactions() { return recentTransactions; }
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
        private final double grossToday;
        private final int transactionsToday;
        private final double refundRate;
        private final int approvalsPending;

        public Kpis(int dailyActiveUsers, int signupsLastSevenDays, double grossToday,
                    int transactionsToday, double refundRate, int approvalsPending) {
            this.dailyActiveUsers = dailyActiveUsers;
            this.signupsLastSevenDays = signupsLastSevenDays;
            this.grossToday = grossToday;
            this.transactionsToday = transactionsToday;
            this.refundRate = refundRate;
            this.approvalsPending = approvalsPending;
        }

        public int getDailyActiveUsers() { return dailyActiveUsers; }
        public int getSignupsLastSevenDays() { return signupsLastSevenDays; }
        public double getGrossToday() { return grossToday; }
        public int getTransactionsToday() { return transactionsToday; }
        public double getRefundRate() { return refundRate; }
        public int getApprovalsPending() { return approvalsPending; }
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

    public static final class TransactionItem {
        private final String id;
        private final String user;
        private final String course;
        private final double amount;
        private final String status;

        public TransactionItem(String id, String user, String course, double amount, String status) {
            this.id = safe(id);
            this.user = safe(user);
            this.course = safe(course);
            this.amount = amount;
            this.status = safe(status);
        }

        public String getId() { return id; }
        public String getUser() { return user; }
        public String getCourse() { return course; }
        public double getAmount() { return amount; }
        public String getStatus() { return status; }
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
