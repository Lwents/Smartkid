package com.example.smartkid.domain;

import com.example.smartkid.data.model.Course;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BusinessRulesTest {

    @Test
    public void validateLogin_rejectsMissingAndShortValues() {
        assertEquals("Vui lòng nhập tài khoản hoặc email",
                BusinessRules.validateLogin("", "123456"));
        assertEquals("Tài khoản phải có ít nhất 3 ký tự",
                BusinessRules.validateLogin("ab", "123456"));
        assertEquals("Mật khẩu phải có ít nhất 6 ký tự",
                BusinessRules.validateLogin("student", "123"));
        assertTrue(BusinessRules.validateLogin("student", "123456").isEmpty());
    }

    @Test
    public void validateRegistration_requiresAValidPhoneNumber() {
        assertEquals("Vui lòng nhập số điện thoại",
                BusinessRules.validateRegistration(
                        "student", "student@example.com", "", "12345678", "12345678"));
        assertEquals("Số điện thoại phải có từ 9 đến 15 chữ số",
                BusinessRules.validateRegistration(
                        "student", "student@example.com", "123", "12345678", "12345678"));
        assertTrue(BusinessRules.validateRegistration(
                "student", "student@example.com", "+84912345678", "12345678", "12345678")
                .isEmpty());
    }

    @Test
    public void validateRegistration_requiresAtLeastEightPasswordCharacters() {
        assertEquals("Mật khẩu phải có ít nhất 8 ký tự",
                BusinessRules.validateRegistration(
                        "student", "student@example.com", "+84912345678",
                        "1234567", "1234567"));
        assertTrue(BusinessRules.validateRegistration(
                "student", "student@example.com", "+84912345678",
                "12345678", "12345678").isEmpty());
    }

    @Test
    public void validateRegistration_rejectsWhitespaceAndUnsupportedUsernameCharacters() {
        String expected = "Tên tài khoản chỉ gồm chữ không dấu, số, dấu chấm, "
                + "gạch dưới hoặc gạch ngang";
        assertEquals(expected, BusinessRules.validateRegistration(
                "lwent kkk", "student@example.com", "+84912345678",
                "12345678", "12345678"));
        assertEquals(expected, BusinessRules.validateRegistration(
                "lwent@kkk", "student@example.com", "+84912345678",
                "12345678", "12345678"));
        assertTrue(BusinessRules.validateRegistration(
                "lwent.kkk_01-test", "student@example.com", "+84912345678",
                "12345678", "12345678").isEmpty());
    }

    @Test
    public void clampProgress_alwaysStaysBetweenZeroAndOneHundred() {
        assertEquals(0, BusinessRules.clampProgress(-30));
        assertEquals(48, BusinessRules.clampProgress(48));
        assertEquals(100, BusinessRules.clampProgress(160));
    }

    @Test
    public void filterCourses_ignoresVietnameseMarksAndSearchesSeveralFields() {
        List<Course> courses = Arrays.asList(
                course("1", "Toán tư duy", "Toán", "Cô Hương"),
                course("2", "Khám phá tự nhiên", "Khoa học", "Thầy Minh")
        );

        List<Course> byTitle = BusinessRules.filterCourses(courses, "toan tu duy");
        List<Course> byTeacher = BusinessRules.filterCourses(courses, "huong");

        assertEquals(1, byTitle.size());
        assertEquals("1", byTitle.get(0).getId());
        assertEquals(1, byTeacher.size());
        assertEquals("1", byTeacher.get(0).getId());
    }

    @Test
    public void sortCourses_createsSortedCopyWithoutChangingSource() {
        Course beta = course("2", "Beta", "", "");
        Course alpha = course("1", "Alpha", "", "");
        List<Course> source = Arrays.asList(beta, alpha);

        List<Course> ascending = BusinessRules.sortCoursesByTitle(source, true);
        List<Course> descending = BusinessRules.sortCoursesByTitle(source, false);

        assertEquals("Alpha", ascending.get(0).getTitle());
        assertEquals("Beta", descending.get(0).getTitle());
        assertEquals("Beta", source.get(0).getTitle());
    }

    private Course course(String id, String title, String subject, String teacher) {
        return new Course(id, title, "Lớp 5", subject, teacher,
                10, 25, "", "", true);
    }
}
