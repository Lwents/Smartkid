package com.example.smartkid.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Bộ test cho toàn bộ quy tắc xác thực của các màn Đăng nhập, Đăng ký,
 * Quên mật khẩu và Đặt lại mật khẩu (BusinessRules).
 */
public class AuthValidationTest {

    // ==================== ĐĂNG NHẬP ====================

    @Test
    public void validateLogin_rejectsNullOrBlankIdentifier() {
        assertEquals("Vui lòng nhập tài khoản hoặc email",
                BusinessRules.validateLogin(null, "123456"));
        assertEquals("Vui lòng nhập tài khoản hoặc email",
                BusinessRules.validateLogin("", "123456"));
        assertEquals("Vui lòng nhập tài khoản hoặc email",
                BusinessRules.validateLogin("   ", "123456"));
    }

    @Test
    public void validateLogin_rejectsIdentifierShorterThanThreeChars() {
        assertEquals("Tài khoản phải có ít nhất 3 ký tự",
                BusinessRules.validateLogin("ab", "123456"));
        // Khoảng trắng hai đầu không được tính vào độ dài
        assertEquals("Tài khoản phải có ít nhất 3 ký tự",
                BusinessRules.validateLogin("  ab  ", "123456"));
    }

    @Test
    public void validateLogin_acceptsIdentifierWithExactlyThreeChars() {
        assertTrue(BusinessRules.validateLogin("abc", "123456").isEmpty());
    }

    @Test
    public void validateLogin_rejectsNullOrEmptyPassword() {
        assertEquals("Vui lòng nhập mật khẩu",
                BusinessRules.validateLogin("student", null));
        assertEquals("Vui lòng nhập mật khẩu",
                BusinessRules.validateLogin("student", ""));
    }

    @Test
    public void validateLogin_rejectsPasswordShorterThanSixChars() {
        assertEquals("Mật khẩu phải có ít nhất 6 ký tự",
                BusinessRules.validateLogin("student", "12345"));
    }

    @Test
    public void validateLogin_acceptsUsernameOrEmailWithValidPassword() {
        assertTrue(BusinessRules.validateLogin("student", "123456").isEmpty());
        assertTrue(BusinessRules.validateLogin("student@example.com", "abc@123").isEmpty());
    }

    // ==================== ĐĂNG KÝ ====================

    @Test
    public void validateRegistration_rejectsShortOrMissingUsername() {
        assertEquals("Tên đăng nhập phải có ít nhất 3 ký tự",
                BusinessRules.validateRegistration(null,
                        "a@b.com", "0912345678", "123456", "123456"));
        assertEquals("Tên đăng nhập phải có ít nhất 3 ký tự",
                BusinessRules.validateRegistration("ab",
                        "a@b.com", "0912345678", "123456", "123456"));
    }

    @Test
    public void validateRegistration_rejectsMalformedEmails() {
        String[] badEmails = {null, "", "abc", "a@b", "a b@c.com", "a@b c.com", "@b.com", "a@.com "};
        for (String email : badEmails) {
            assertEquals("email không hợp lệ phải bị từ chối: " + email,
                    "Email không đúng định dạng",
                    BusinessRules.validateRegistration("student",
                            email, "0912345678", "123456", "123456"));
        }
    }

    @Test
    public void validateRegistration_rejectsMissingOrInvalidPhone() {
        assertEquals("Vui lòng nhập số điện thoại",
                BusinessRules.validateRegistration("student",
                        "a@b.com", null, "123456", "123456"));
        assertEquals("Vui lòng nhập số điện thoại",
                BusinessRules.validateRegistration("student",
                        "a@b.com", "   ", "123456", "123456"));
        String[] badPhones = {"123", "12345678", "1234567890123456", "09abc12345", "+84 912345678"};
        for (String phone : badPhones) {
            assertEquals("số điện thoại không hợp lệ phải bị từ chối: " + phone,
                    "Số điện thoại phải có từ 9 đến 15 chữ số",
                    BusinessRules.validateRegistration("student",
                            "a@b.com", phone, "123456", "123456"));
        }
    }

    @Test
    public void validateRegistration_acceptsPhoneBoundaries() {
        // 9 chữ số (cận dưới), 15 chữ số (cận trên), có tiền tố +
        assertTrue(BusinessRules.validateRegistration("student",
                "a@b.com", "123456789", "123456", "123456").isEmpty());
        assertTrue(BusinessRules.validateRegistration("student",
                "a@b.com", "123456789012345", "123456", "123456").isEmpty());
        assertTrue(BusinessRules.validateRegistration("student",
                "a@b.com", "+84912345678", "123456", "123456").isEmpty());
    }

    @Test
    public void validateRegistration_rejectsShortPasswordAndMismatch() {
        assertEquals("Mật khẩu phải có ít nhất 6 ký tự",
                BusinessRules.validateRegistration("student",
                        "a@b.com", "0912345678", "12345", "12345"));
        assertEquals("Mật khẩu phải có ít nhất 6 ký tự",
                BusinessRules.validateRegistration("student",
                        "a@b.com", "0912345678", null, null));
        assertEquals("Mật khẩu nhập lại không khớp",
                BusinessRules.validateRegistration("student",
                        "a@b.com", "0912345678", "123456", "654321"));
    }

    @Test
    public void validateRegistration_acceptsFullyValidInput() {
        assertTrue(BusinessRules.validateRegistration("hocvien01",
                "hocvien01@example.com", "0912345678", "matkhau123", "matkhau123").isEmpty());
    }

    // ==================== QUÊN MẬT KHẨU ====================

    @Test
    public void validateForgotPasswordEmail_rejectsInvalidEmail() {
        assertEquals("Vui lòng nhập đúng địa chỉ email",
                BusinessRules.validateForgotPasswordEmail(null));
        assertEquals("Vui lòng nhập đúng địa chỉ email",
                BusinessRules.validateForgotPasswordEmail(""));
        assertEquals("Vui lòng nhập đúng địa chỉ email",
                BusinessRules.validateForgotPasswordEmail("khong-phai-email"));
    }

    @Test
    public void validateForgotPasswordEmail_acceptsValidEmail() {
        assertTrue(BusinessRules.validateForgotPasswordEmail("a@b.com").isEmpty());
        assertTrue(BusinessRules.validateForgotPasswordEmail(" a@b.com ").isEmpty());
    }

    // ==================== ĐẶT LẠI MẬT KHẨU ====================

    @Test
    public void validateResetPassword_checksEveryFieldInOrder() {
        assertEquals("Email không đúng định dạng",
                BusinessRules.validateResetPassword("sai", "token", "123456", "123456"));
        assertEquals("Vui lòng nhập mã đặt lại mật khẩu trong email",
                BusinessRules.validateResetPassword("a@b.com", "  ", "123456", "123456"));
        assertEquals("Vui lòng nhập mã đặt lại mật khẩu trong email",
                BusinessRules.validateResetPassword("a@b.com", null, "123456", "123456"));
        assertEquals("Mật khẩu mới phải có ít nhất 6 ký tự",
                BusinessRules.validateResetPassword("a@b.com", "token", "12345", "12345"));
        assertEquals("Mật khẩu nhập lại không khớp",
                BusinessRules.validateResetPassword("a@b.com", "token", "123456", "1234567"));
        assertTrue(BusinessRules.validateResetPassword(
                "a@b.com", "token-abc", "123456", "123456").isEmpty());
    }

    // ==================== isEmail ====================

    @Test
    public void isEmail_coversCommonEdgeCases() {
        assertTrue(BusinessRules.isEmail("stu725105101@hnue.edu.vn"));
        assertTrue(BusinessRules.isEmail("ten.ho+tag@sub.domain.vn"));
        assertFalse(BusinessRules.isEmail(null));
        assertFalse(BusinessRules.isEmail("a@@b.com"));
        assertFalse(BusinessRules.isEmail("a@b_com"));
    }
}
