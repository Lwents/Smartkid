package com.example.smartkid.data.repository;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ManagementRepositoryNotificationTest {
    @Test
    public void zeroRecipients_isNotPresentedAsSent() {
        assertEquals("Không có người nhận",
                ManagementRepository.notificationDeliveryStatus(0));
    }

    @Test
    public void positiveRecipients_isPresentedAsSent() {
        assertEquals("Đã gửi",
                ManagementRepository.notificationDeliveryStatus(3));
    }
}
