package com.example.smartkid.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.smartkid.R;

/**
 * Vòng tròn tiến độ ở màn Phân tích học tập: một vòng nền mờ và một cung tô đậm
 * chạy theo phần trăm, hai đầu cung bo tròn. Phần trăm được vẽ ngay giữa vòng.
 */
public class RingProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int percent;

    public RingProgressView(Context context) {
        this(context, null);
    }

    public RingProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        float stroke = 14 * density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.ring_track));

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(ContextCompat.getColor(context, R.color.smartkid_primary));

        numberPaint.setColor(ContextCompat.getColor(context, R.color.smartkid_primary));
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setFakeBoldText(true);
        numberPaint.setTextSize(34 * density);

        percentPaint.setColor(ContextCompat.getColor(context, R.color.smartkid_primary));
        percentPaint.setTextAlign(Paint.Align.LEFT);
        percentPaint.setTextSize(17 * density);
    }

    /** Đặt phần trăm hoàn thành (tự kẹp về khoảng 0-100). */
    public void setPercent(int value) {
        percent = Math.max(0, Math.min(100, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = arcPaint.getStrokeWidth() / 2f + 2;
        oval.set(inset, inset, getWidth() - inset, getHeight() - inset);

        canvas.drawArc(oval, 0, 360, false, trackPaint);
        // Bắt đầu từ đỉnh vòng tròn (-90 độ) rồi chạy theo chiều kim đồng hồ.
        canvas.drawArc(oval, -90, percent * 3.6f, false, arcPaint);

        // Vẽ "8" và "%" cạnh nhau, canh sao cho cả cụm nằm giữa vòng.
        String number = String.valueOf(percent);
        float numberWidth = numberPaint.measureText(number);
        float percentWidth = percentPaint.measureText("%");
        float startX = getWidth() / 2f - (numberWidth + percentWidth) / 2f;
        float baseline = getHeight() / 2f - (numberPaint.descent() + numberPaint.ascent()) / 2f;
        canvas.drawText(number, startX + numberWidth / 2f, baseline, numberPaint);
        canvas.drawText("%", startX + numberWidth, baseline, percentPaint);
    }
}
