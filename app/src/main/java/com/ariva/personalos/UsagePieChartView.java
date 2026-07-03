package com.ariva.personalos;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;

public class UsagePieChartView extends View implements ViewTreeObserver.OnScrollChangedListener {
    private final String[] labels;
    private final long[] values;
    private final int[] colors;
    private final long total;
    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Rect visibleBounds = new Rect();
    private ViewTreeObserver registeredObserver;
    private ValueAnimator entranceAnimator;
    private float animationProgress;
    private boolean animationStarted;
    private int selectedIndex = -1;

    public UsagePieChartView(Context context, String[] labels, long[] values,
                             int[] colors, int centerColor) {
        super(context);
        this.labels = labels;
        this.values = values;
        this.colors = colors;
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        total = sum;
        holePaint.setColor(Color.WHITE);
        centerPaint.setColor(centerColor);
        centerPaint.setTextAlign(Paint.Align.CENTER);
        centerPaint.setTypeface(Typeface.DEFAULT_BOLD);
        centerPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 10f);
        setContentDescription("App usage by category pie chart");
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registeredObserver = getViewTreeObserver();
        registeredObserver.addOnScrollChangedListener(this);
        post(this::startAnimationIfVisible);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeScrollListener();
        if (entranceAnimator != null) {
            entranceAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onScrollChanged() {
        startAnimationIfVisible();
    }

    private void startAnimationIfVisible() {
        if (animationStarted || getHeight() <= 0 || !getGlobalVisibleRect(visibleBounds)) {
            return;
        }
        if (visibleBounds.height() < getHeight() * 0.3f) {
            return;
        }
        animationStarted = true;
        removeScrollListener();
        entranceAnimator = ValueAnimator.ofFloat(0f, 1f);
        entranceAnimator.setDuration(1200L);
        entranceAnimator.setInterpolator(new DecelerateInterpolator());
        entranceAnimator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        entranceAnimator.start();
    }

    private void removeScrollListener() {
        if (registeredObserver != null && registeredObserver.isAlive()) {
            registeredObserver.removeOnScrollChangedListener(this);
        }
        registeredObserver = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = touchedSliceIndex(event.getX(), event.getY());
            if (index >= 0) {
                selectedIndex = index;
                setContentDescription(labels[index] + ", " + formatDuration(values[index]));
                performClick();
                invalidate();
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (total <= 0 || values.length == 0) {
            return;
        }

        float size = Math.min(getWidth(), getHeight()) - 8f;
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        bounds.set(left, top, left + size, top + size);

        float startAngle = -90f;
        float remainingSweep = 360f * animationProgress;
        for (int index = 0; index < values.length; index++) {
            float sweep = values[index] * 360f / total;
            slicePaint.setColor(index == selectedIndex ? darken(colors[index]) : colors[index]);
            float visibleSweep = Math.min(sweep, Math.max(0f, remainingSweep));
            if (visibleSweep > 0f) {
                canvas.drawArc(bounds, startAngle, visibleSweep, true, slicePaint);
            }
            startAngle += sweep;
            remainingSweep -= sweep;
        }

        float holeRadius = size * 0.28f;
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, holeRadius, holePaint);
        if (selectedIndex >= 0) {
            Paint.FontMetrics metrics = centerPaint.getFontMetrics();
            canvas.drawText(
                    formatDuration(values[selectedIndex]),
                    getWidth() / 2f,
                    getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f,
                    centerPaint);
        }
    }

    private int touchedSliceIndex(float x, float y) {
        if (total <= 0) {
            return -1;
        }
        float size = Math.min(getWidth(), getHeight()) - 8f;
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        double distance = Math.hypot(x - centerX, y - centerY);
        if (distance < size * 0.28f || distance > size / 2f) {
            return -1;
        }

        double angle = Math.toDegrees(Math.atan2(y - centerY, x - centerX)) + 90.0;
        if (angle < 0) {
            angle += 360.0;
        }
        float cumulative = 0f;
        for (int index = 0; index < values.length; index++) {
            cumulative += values[index] * 360f / total;
            if (angle <= cumulative) {
                return index;
            }
        }
        return values.length - 1;
    }

    private static int darken(int color) {
        return Color.rgb(
                Math.round(Color.red(color) * 0.65f),
                Math.round(Color.green(color) * 0.65f),
                Math.round(Color.blue(color) * 0.65f));
    }

    private static String formatDuration(long millis) {
        if (millis > 0 && millis < 60000L) {
            return "<1m";
        }
        long minutes = Math.max(0, millis / 60000L);
        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        return hours > 0 ? hours + "h " + remainingMinutes + "m" : minutes + "m";
    }
}
