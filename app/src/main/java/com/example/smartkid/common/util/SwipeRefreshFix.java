package com.example.smartkid.common.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;

import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * SwipeRefreshLayout chỉ hỏi con trực tiếp "còn cuộn lên được không?". Khi con trực
 * tiếp là FrameLayout/LinearLayout bọc ngoài list, nó luôn trả lời "không" nên vuốt
 * ở giữa danh sách vẫn kích hoạt tải lại trang. Helper này trỏ câu hỏi đó tới đúng
 * view cuộn được bên trong.
 */
public final class SwipeRefreshFix {
    private SwipeRefreshFix() {
    }

    /** Gắn cho một hoặc nhiều SwipeRefreshLayout; bỏ qua tham số null. */
    public static void attach(SwipeRefreshLayout... layouts) {
        if (layouts == null) return;
        for (SwipeRefreshLayout layout : layouts) {
            if (layout == null) continue;
            layout.setOnChildScrollUpCallback((parent, child) -> {
                View scrollable = findScrollable(parent);
                return scrollable != null && canScrollUp(scrollable);
            });
        }
    }

    /** View cuộn được đầu tiên theo chiều sâu (ListView, RecyclerView, ScrollView...). */
    private static View findScrollable(View view) {
        if (isScrollable(view)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child.getVisibility() != View.VISIBLE) continue;
            View found = findScrollable(child);
            if (found != null) return found;
        }
        return null;
    }

    /** Kiểm tra một view có thuộc loại cuộn được không. */
    private static boolean isScrollable(View view) {
        return view instanceof AbsListView
                || view instanceof RecyclerView
                || view instanceof NestedScrollView
                || view instanceof ScrollView;
    }

    /** Hỏi view đó còn cuộn lên được nữa không - căn cứ để cho phép kéo tải lại. */
    private static boolean canScrollUp(View view) {
        if (view instanceof AbsListView) {
            AbsListView list = (AbsListView) view;
            return list.getChildCount() > 0
                    && (list.getFirstVisiblePosition() > 0
                    || list.getChildAt(0).getTop() < list.getPaddingTop());
        }
        return view.canScrollVertically(-1) || ViewCompat.canScrollVertically(view, -1);
    }
}
