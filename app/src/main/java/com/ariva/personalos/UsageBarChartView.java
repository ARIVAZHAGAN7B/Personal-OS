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

import java.util.Locale;

public class UsageBarChartView extends View implements ViewTreeObserver.OnScrollChangedListener {
    public interface OnBarClickListener {
        void onBarClick(int index);
    }

    private final long[] values;
    private final String[] labels;
    private final OnBarClickListener barClickListener;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barBounds = new RectF();
    private final Rect visibleBounds = new Rect();
    private ViewTreeObserver registeredObserver;
    private ValueAnimator entranceAnimator;
    private float animationProgress;
    private boolean animationStarted;
    private int selectedIndex = -1;

    public UsageBarChartView(Context context, long[] values, String[] labels,
                             int accentColor, int selectedColor,
                             OnBarClickListener barClickListener) {
        super(context);
        this.values = values;
        this.labels = labels;
        this.barClickListener = barClickListener;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;

        barPaint.setColor(accentColor);
        selectedBarPaint.setColor(selectedColor);
        gridPaint.setColor(Color.rgb(224, 228, 236));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f, 8f}, 0f));
        labelPaint.setColor(Color.rgb(91, 107, 123));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(scaledDensity * 10f);
        valuePaint.setColor(selectedColor);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.DEFAULT_BOLD);
        valuePaint.setTextSize(scaledDensity * 10f);
        axisPaint.setColor(Color.rgb(91, 107, 123));
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        axisPaint.setTextSize(scaledDensity * 8f);
        setContentDescription("Screen time bar chart");
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
        entranceAnimator.setDuration(700L);
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
            int index = touchedBarIndex(event.getX(), event.getY());
            if (index >= 0) {
                selectedIndex = index;
                setContentDescription(labels[index] + ", " + formatDuration(values[index]));
                performClick();
                invalidate();
                if (barClickListener != null) {
                    barClickListener.onBarClick(index);
                }
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
        if (values == null || values.length == 0) {
            return;
        }

        long axisMax = niceAxisMax(maxValue());
        float density = getResources().getDisplayMetrics().density;
        float left = 46f * density;
        float right = getWidth() - 6f * density;
        float top = 20f * density;
        float bottom = getHeight() - 30f * density;
        float chartHeight = Math.max(1f, bottom - top);
        float slot = Math.max(1f, (right - left) / values.length);
        float barWidth = Math.max(2f * density, Math.min(slot * 0.56f, 36f * density));
        float radius = Math.min(4f * density, barWidth * 0.22f);

        drawYAxis(canvas, left, right, top, chartHeight, axisMax, density);
        for (int index = 0; index < values.length; index++) {
            float centerX = left + slot * index + slot / 2f;
            float fullHeight = values[index] <= 0 ? 0f
                    : Math.max(3f * density, chartHeight * values[index] / axisMax);
            float fillHeight = fullHeight * animationProgress;
            float fillTop = bottom - fillHeight;
            barBounds.set(centerX - barWidth / 2f, fillTop, centerX + barWidth / 2f, bottom);
            canvas.drawRoundRect(barBounds, radius, radius,
                    index == selectedIndex ? selectedBarPaint : barPaint);

            if (index == selectedIndex) {
                float valueY = Math.max(top + 10f * density, fillTop - 6f * density);
                canvas.drawText(formatDuration(values[index]), centerX, valueY, valuePaint);
            }
            if (labels != null && index < labels.length && !labels[index].isEmpty()) {
                canvas.drawText(labels[index], centerX, getHeight() - 12f * density, labelPaint);
            }
        }
    }

    private void drawYAxis(Canvas canvas, float left, float right, float top,
                           float chartHeight, long axisMax, float density) {
        for (int tick = 0; tick <= 4; tick++) {
            long value = axisMax * (4 - tick) / 4;
            float y = top + chartHeight * tick / 4f;
            canvas.drawText(formatAxisValue(value), left - 8f * density, y + 3f * density, axisPaint);
            canvas.drawLine(left, y, right, y, gridPaint);
        }
    }

    private int touchedBarIndex(float x, float y) {
        if (maxValue() <= 0) {
            return -1;
        }
        float density = getResources().getDisplayMetrics().density;
        float left = 46f * density;
        float right = getWidth() - 6f * density;
        float top = 20f * density;
        float bottom = getHeight() - 30f * density;
        if (x < left || x > right || y < top || y > bottom) {
            return -1;
        }
        float slot = (right - left) / values.length;
        int index = Math.min(values.length - 1, (int) ((x - left) / slot));
        return values[index] > 0 ? index : -1;
    }

    private long maxValue() {
        long max = 0;
        for (long value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static long niceAxisMax(long maxValue) {
        if (maxValue <= 0) {
            return 60L * 60L * 1000L;
        }
        double rawStep = maxValue / 4.0;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude;
        double factor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
        return Math.max(4L, (long) Math.ceil(factor * magnitude) * 4L);
    }

    private static String formatAxisValue(long millis) {
        long minutes = Math.max(0, millis / 60000L);
        if (minutes >= 60) {
            double hours = minutes / 60.0;
            return Math.abs(hours - Math.rint(hours)) < 0.05
                    ? ((long) Math.rint(hours)) + "h"
                    : String.format(Locale.US, "%.1fh", hours);
        }
        return minutes + "m";
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
