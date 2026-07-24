package com.example.smartkid.feature.admin.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native equivalent of the Flutter activity chart painter. */
public final class AdminActivityChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private List<String> labels = Collections.emptyList();
    private List<Float> values = Collections.emptyList();

    public AdminActivityChartView(Context context) {
        super(context);
    }

    public AdminActivityChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AdminActivityChartView(Context context, @Nullable AttributeSet attrs, int style) {
        super(context, attrs, style);
    }

    public void setData(List<String> chartLabels, List<Float> chartValues) {
        labels = chartLabels == null
                ? Collections.emptyList() : new ArrayList<>(chartLabels);
        values = chartValues == null
                ? Collections.emptyList() : new ArrayList<>(chartValues);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.isEmpty()) return;

        float top = dp(25);
        float bottom = getHeight() - dp(30);
        float left = dp(15);
        float right = getWidth() - dp(15);
        float chartWidth = right - left;
        float chartHeight = bottom - top;

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(153, 226, 232, 240));
        for (int row = 0; row <= 3; row++) {
            float y = top + chartHeight * row / 3f;
            canvas.drawLine(left, y, right, y, paint);
        }

        float maxValue = 0;
        for (float value : values) maxValue = Math.max(maxValue, value);
        float maxY = maxValue == 0 ? 10 : maxValue * 1.25f;
        float stepX = values.size() > 1 ? chartWidth / (values.size() - 1f) : chartWidth / 2f;
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            xs.add(left + index * stepX);
            ys.add(top + chartHeight * (1f - values.get(index) / maxY));
        }

        linePath.reset();
        fillPath.reset();
        linePath.moveTo(xs.get(0), ys.get(0));
        fillPath.moveTo(xs.get(0), bottom);
        fillPath.lineTo(xs.get(0), ys.get(0));
        for (int index = 0; index < values.size() - 1; index++) {
            float currentX = xs.get(index);
            float currentY = ys.get(index);
            float nextX = xs.get(index + 1);
            float nextY = ys.get(index + 1);
            float control1 = currentX + (nextX - currentX) * 0.4f;
            float control2 = currentX + (nextX - currentX) * 0.6f;
            linePath.cubicTo(control1, currentY, control2, nextY, nextX, nextY);
            fillPath.cubicTo(control1, currentY, control2, nextY, nextX, nextY);
        }
        fillPath.lineTo(xs.get(xs.size() - 1), bottom);
        fillPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{Color.argb(89, 99, 91, 255), Color.argb(20, 56, 189, 248),
                        Color.TRANSPARENT},
                new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(new LinearGradient(left, 0, right, 0,
                new int[]{Color.rgb(99, 91, 255), Color.rgb(59, 130, 246),
                        Color.rgb(56, 189, 248)}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(linePath, paint);
        paint.setShader(null);

        paint.setTextAlign(Paint.Align.CENTER);
        for (int index = 0; index < values.size(); index++) {
            float x = xs.get(index);
            float y = ys.get(index);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(46, 99, 91, 255));
            canvas.drawCircle(x, y, dp(8), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(5), paint);
            paint.setColor(Color.rgb(99, 91, 255));
            canvas.drawCircle(x, y, dp(3), paint);

            paint.setColor(Color.rgb(71, 85, 105));
            paint.setTextSize(sp(10));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float value = values.get(index);
            String valueLabel = value == Math.round(value)
                    ? String.valueOf(Math.round(value)) : String.format("%.1f", value);
            canvas.drawText(valueLabel, x, y - dp(11), paint);

            if (index < labels.size()) {
                paint.setColor(Color.rgb(100, 116, 139));
                paint.setTextSize(sp(11));
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                canvas.drawText(labels.get(index), x, bottom + dp(20), paint);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
