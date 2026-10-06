package com.example.screengoblin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HollowPieChartView extends View {

    public static class PieSlice {
        public String name;
        public long value;
        public float percentage; // 0.0 to 1.0
        public int color;

        public PieSlice(String name, long value, float percentage, int color) {
            this.name = name;
            this.value = value;
            this.percentage = percentage;
            this.color = color;
        }
    }

    private final List<PieSlice> slices = new ArrayList<>();
    private Paint arcPaint;
    private RectF arcBounds;

    public HollowPieChartView(Context context) {
        super(context);
        init();
    }

    public HollowPieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HollowPieChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcBounds = new RectF();
    }

    public void setSlices(List<PieSlice> newSlices) {
        slices.clear();
        if (newSlices != null) {
            slices.addAll(newSlices);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        int size = Math.min(width, height);
        float strokeWidth = size * 0.24f; // Thickness of the hollow ring
        arcPaint.setStrokeWidth(strokeWidth);

        float padding = strokeWidth / 2f + 4f;
        float left = (width - size) / 2f + padding;
        float top = (height - size) / 2f + padding;
        float right = (width + size) / 2f - padding;
        float bottom = (height + size) / 2f - padding;

        arcBounds.set(left, top, right, bottom);

        if (slices.isEmpty()) {
            // Draw an empty subtle placeholder ring
            arcPaint.setColor(Color.parseColor("#22888888"));
            canvas.drawArc(arcBounds, 0, 360, false, arcPaint);
            return;
        }

        float currentAngle = -90f; // Start at top (12 o'clock)
        for (PieSlice slice : slices) {
            float sweepAngle = slice.percentage * 360f;
            if (sweepAngle <= 0) continue;

            arcPaint.setColor(slice.color);
            // Leave a tiny 1-degree gap between slices if multiple slices exist
            float drawSweep = slices.size() > 1 ? Math.max(1f, sweepAngle - 1.5f) : sweepAngle;
            canvas.drawArc(arcBounds, currentAngle, drawSweep, false, arcPaint);
            currentAngle += sweepAngle;
        }
    }
}
