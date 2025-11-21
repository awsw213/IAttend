package com.example.iattend.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class BarChartView extends View {
    private final Paint barPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int checked = 0;
    private int total = 0;

    public BarChartView(Context context) { super(context); init(); }
    public BarChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public BarChartView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        barPaint1.setColor(0xFF1976D2);
        barPaint2.setColor(0xFF90CAF9);
        bgPaint.setColor(0xFFE3F2FD);
    }

    public void setData(int checked, int total) {
        this.checked = Math.max(0, checked);
        this.total = Math.max(0, total);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float padding = dp(12);
        RectF bg = new RectF(padding, padding, w - padding, h - padding);
        canvas.drawRoundRect(bg, dp(12), dp(12), bgPaint);
        float barWidth = (w - padding * 3) / 2f;
        float maxHeight = h - padding * 2;
        float ratio = total > 0 ? ((float) checked / (float) total) : 0f;
        float h1 = maxHeight * ratio;
        float h2 = maxHeight;
        RectF r1 = new RectF(padding, h - padding - h1, padding + barWidth, h - padding);
        RectF r2 = new RectF(padding * 2 + barWidth, h - padding - h2, padding * 2 + barWidth * 2, h - padding);
        canvas.drawRoundRect(r1, dp(8), dp(8), barPaint1);
        canvas.drawRoundRect(r2, dp(8), dp(8), barPaint2);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}

