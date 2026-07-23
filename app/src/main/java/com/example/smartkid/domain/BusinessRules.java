package com.example.smartkid.domain;

import com.example.smartkid.data.model.Course;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Các nghiệp vụ thuần Java, độc lập với Activity và giao diện. */
public final class BusinessRules {
    private BusinessRules() {
    }

    public static String validateLogin(String identifier, String password) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return "Vui lòng nhập tài khoản hoặc email";
        }
        if (identifier.trim().length() < 3) {
            return "Tài khoản phải có ít nhất 3 ký tự";
        }
        if (password == null || password.isEmpty()) {
            return "Vui lòng nhập mật khẩu";
        }
        if (password.length() < 6) {
            return "Mật khẩu phải có ít nhất 6 ký tự";
        }
        return "";
    }

    public static String validateRegistration(String username, String email, String phone,
                                              String password, String confirmation) {
        if (username == null || username.trim().length() < 3) {
            return "Tên đăng nhập phải có ít nhất 3 ký tự";
        }
        if (!isEmail(email)) {
            return "Email không đúng định dạng";
        }
        String normalizedPhone = phone == null ? "" : phone.trim();
        if (!normalizedPhone.isEmpty() && !normalizedPhone.matches("^[0-9+]{9,15}$")) {
            return "Số điện thoại phải có từ 9 đến 15 chữ số";
        }
        if (password == null || password.length() < 6) {
            return "Mật khẩu phải có ít nhất 6 ký tự";
        }
        if (!password.equals(confirmation)) {
            return "Mật khẩu nhập lại không khớp";
        }
        return "";
    }

    public static String validateForgotPasswordEmail(String email) {
        return isEmail(email) ? "" : "Vui lòng nhập đúng địa chỉ email";
    }

    public static String validateResetPassword(String email, String token,
                                               String password, String confirmation) {
        if (!isEmail(email)) {
            return "Email không đúng định dạng";
        }
        if (token == null || token.trim().isEmpty()) {
            return "Vui lòng nhập mã đặt lại mật khẩu trong email";
        }
        if (password == null || password.length() < 6) {
            return "Mật khẩu mới phải có ít nhất 6 ký tự";
        }
        if (!password.equals(confirmation)) {
            return "Mật khẩu nhập lại không khớp";
        }
        return "";
    }

    public static boolean isEmail(String value) {
        if (value == null) {
            return false;
        }
        return value.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    public static List<Course> filterCourses(List<Course> source, String keyword) {
        List<Course> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            result.addAll(source);
            return result;
        }
        for (Course course : source) {
            if (course == null) {
                continue;
            }
            String searchable = normalize(course.getTitle() + " " + course.getSubject()
                    + " " + course.getTeacherName());
            if (searchable.contains(normalizedKeyword)) {
                result.add(course);
            }
        }
        return result;
    }

    public static List<Course> sortCoursesByTitle(List<Course> source, boolean ascending) {
        List<Course> result = source == null ? new ArrayList<>() : new ArrayList<>(source);
        Comparator<Course> comparator = (left, right) -> {
            String leftTitle = left == null ? "" : left.getTitle();
            String rightTitle = right == null ? "" : right.getTitle();
            return leftTitle.compareToIgnoreCase(rightTitle);
        };
        Collections.sort(result, ascending ? comparator : comparator.reversed());
        return result;
    }

    public static int clampProgress(int progress) {
        return Math.max(0, Math.min(100, progress));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutMarks.toLowerCase(Locale.ROOT).trim();
    }
}
