package com.example.iattend.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public class ProgressArcView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float progress = 0f;

    public ProgressArcView(Context context) { super(context); init(); }
    public ProgressArcView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public ProgressArcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(16f);
        bgPaint.setColor(0xFFE0E0E0);
        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeCap(Paint.Cap.ROUND);
        fgPaint.setStrokeWidth(16f);
        fgPaint.setColor(0xFF2196F3);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float pad = 16f;
        oval.set(pad, pad, w - pad, h - pad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawArc(oval, 0, 360, false, bgPaint);
        canvas.drawArc(oval, -90, progress * 360f, false, fgPaint);
    }

    public void animateTo(float target) {
        float clamped = Math.max(0f, Math.min(1f, target));
        ValueAnimator anim = ValueAnimator.ofFloat(progress, clamped);
        anim.setDuration(800);
        anim.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }
}