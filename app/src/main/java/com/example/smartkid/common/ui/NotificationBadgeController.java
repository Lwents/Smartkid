package com.example.smartkid.common.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import com.example.smartkid.R;

/** Renders a numeric unread badge and a restrained repeating bell pulse. */
public final class NotificationBadgeController {
    private final Context context;
    private final View bell;
    private final TextView badge;
    private AnimatorSet pulse;
    private int currentUnread = -1;

    public NotificationBadgeController(Context context, View bell, TextView badge) {
        this.context = context;
        this.bell = bell;
        this.badge = badge;
    }

    public void render(int unread) {
        int safeUnread = Math.max(0, unread);
        if (safeUnread == 0) {
            currentUnread = 0;
            stopPulse();
            badge.animate().cancel();
            badge.setVisibility(View.GONE);
            bell.setContentDescription(context.getString(R.string.open_notifications));
            return;
        }

        boolean changed = safeUnread != currentUnread;
        currentUnread = safeUnread;
        badge.setText(safeUnread > 99 ? "99+" : String.valueOf(safeUnread));
        badge.setVisibility(View.VISIBLE);
        if (changed) {
            badge.animate().cancel();
            badge.setAlpha(0f);
            badge.setScaleX(0.55f);
            badge.setScaleY(0.55f);
            badge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240L).start();
        }
        bell.setContentDescription(context.getString(
                R.string.role_notification_badge_description, safeUnread));
        startPulse();
    }

    public void clear() {
        stopPulse();
        badge.animate().cancel();
    }

    boolean isPulsing() {
        return pulse != null && pulse.isStarted();
    }

    private void startPulse() {
        if (pulse != null && pulse.isRunning()) return;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(
                bell, View.SCALE_X, 1f, 1.08f, 1f, 1f, 1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(
                bell, View.SCALE_Y, 1f, 1.08f, 1f, 1f, 1f, 1f);
        for (ObjectAnimator animator : new ObjectAnimator[]{scaleX, scaleY}) {
            animator.setDuration(2600L);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
        }
        pulse = new AnimatorSet();
        pulse.playTogether(scaleX, scaleY);
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        bell.animate().cancel();
        bell.setScaleX(1f);
        bell.setScaleY(1f);
    }
}
