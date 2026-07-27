package com.example.smartkid.common.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.smartkid.R;

import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class NotificationBadgeControllerTest {
    @Test
    public void unreadBadgeAppearsAndThenFullyDisappearsAtZero() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View bell = new FrameLayout(context);
            TextView badge = new TextView(context);
            badge.setVisibility(View.GONE);
            NotificationBadgeController controller =
                    new NotificationBadgeController(context, bell, badge);

            controller.render(3);
            assertEquals(View.VISIBLE, badge.getVisibility());
            assertEquals("3", badge.getText().toString());
            assertEquals(context.getString(R.string.role_notification_badge_description, 3),
                    bell.getContentDescription().toString());
            assertTrue(controller.isPulsing());

            controller.render(0);
            assertEquals(View.GONE, badge.getVisibility());
            assertEquals(1f, bell.getScaleX(), 0f);
            assertEquals(1f, bell.getScaleY(), 0f);
            assertEquals(context.getString(R.string.open_notifications),
                    bell.getContentDescription().toString());
            assertFalse(controller.isPulsing());
            controller.clear();
        });
    }
}
