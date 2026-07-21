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

import java.util.ArrayList;
import java.util.Locale;

final class ExpenseCharts {
    static final int COLOR_NAVY = Color.rgb(22, 63, 95);
    static final int COLOR_RED = Color.rgb(180, 35, 24);
    static final int COLOR_GREEN = Color.rgb(15, 157, 88);
    static final int COLOR_MUTED = Color.rgb(91, 107, 123);
    static final int COLOR_BORDER = Color.rgb(224, 228, 236);

    private ExpenseCharts() {
    }

    static int darkenChartColor(int color) {
        return Color.rgb(
                Math.round(Color.red(color) * 0.65f),
                Math.round(Color.green(color) * 0.65f),
                Math.round(Color.blue(color) * 0.65f));
    }

    static long niceAxisMax(long maxValue) {
        if (maxValue <= 0) {
            return 10000L;
        }
        double rawStep = maxValue / 4.0;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude;
        double factor;
        if (normalized <= 1) {
            factor = 1;
        } else if (normalized <= 2) {
            factor = 2;
        } else if (normalized <= 5) {
            factor = 5;
        } else {
            factor = 10;
        }
        return Math.max(4L, (long) Math.ceil(factor * magnitude) * 4L);
    }

    static String formatAxisTick(long valueInCents) {
        double rupees = valueInCents / 100.0;
        if (rupees >= 10000000) {
            return compactAxisValue(rupees / 10000000.0) + "Cr";
        }
        if (rupees >= 100000) {
            return compactAxisValue(rupees / 100000.0) + "L";
        }
        if (rupees >= 1000) {
            return compactAxisValue(rupees / 1000.0) + "K";
        }
        return String.valueOf(Math.round(rupees));
    }

    static String compactAxisValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    static void drawYAxis(Canvas canvas, Paint axisPaint, Paint gridPaint,
                          float left, float right, float top, float chartHeight,
                          long axisMax, float density) {
        for (int tick = 0; tick <= 4; tick++) {
            long tickValue = axisMax * (4 - tick) / 4;
            float y = top + chartHeight * tick / 4f;
            canvas.drawText(formatAxisTick(tickValue), left - 8f * density, y + 3f * density, axisPaint);
            canvas.drawLine(left, y, right, y, gridPaint);
        }
    }

    static float chartBarRadius(float barWidth, float density) {
        return Math.min(4f * density, barWidth * 0.22f);
    }
}

class PieSlice {
    final String label;
    final long amount;
    final int color;

    PieSlice(String label, long amount, int color) {
        this.label = label;
        this.amount = amount;
        this.color = color;
    }
}

abstract class AnimatedChartView extends View
        implements ViewTreeObserver.OnScrollChangedListener {
    protected float animationProgress;
    private ValueAnimator entranceAnimator;
    private final Rect visibleBounds = new Rect();
    private ViewTreeObserver registeredObserver;
    private boolean animationStarted;

    AnimatedChartView(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animationStarted) {
            return;
        }
        registeredObserver = getViewTreeObserver();
        registeredObserver.addOnScrollChangedListener(this);
        post(this::startAnimationIfVisible);
    }

    @Override
    public void onScrollChanged() {
        startAnimationIfVisible();
    }

    private void startAnimationIfVisible() {
        if (animationStarted || getHeight() <= 0 || !getGlobalVisibleRect(visibleBounds)) {
            return;
        }
        if (visibleBounds.height() < getHeight() * 0.3f
                || visibleBounds.width() < getWidth() * 0.3f) {
            return;
        }

        animationStarted = true;
        removeScrollListener();
        entranceAnimator = ValueAnimator.ofFloat(0f, 1f);
        entranceAnimator.setDuration(animationDuration());
        entranceAnimator.setInterpolator(new DecelerateInterpolator());
        entranceAnimator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        entranceAnimator.start();
    }

    protected long animationDuration() {
        return 700L;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeScrollListener();
        if (entranceAnimator != null) {
            entranceAnimator.cancel();
            entranceAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    private void removeScrollListener() {
        if (registeredObserver != null && registeredObserver.isAlive()) {
            registeredObserver.removeOnScrollChangedListener(this);
        }
        registeredObserver = null;
    }
}

class PieChartView extends AnimatedChartView {
    private final ArrayList<PieSlice> slices;
    private final long total;
    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerAmountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private int selectedIndex = -1;

    PieChartView(Context context, ArrayList<PieSlice> slices, long total) {
        super(context);
        this.slices = slices;
        this.total = total;
        holePaint.setColor(Color.WHITE);
        centerAmountPaint.setColor(ExpenseCharts.COLOR_NAVY);
        centerAmountPaint.setTextAlign(Paint.Align.CENTER);
        centerAmountPaint.setTypeface(Typeface.DEFAULT_BOLD);
        centerAmountPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
        setWillNotDraw(false);
    }

    @Override
    protected long animationDuration() {
        return 1200L;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = touchedSliceIndex(event.getX(), event.getY());
            if (index >= 0) {
                selectedIndex = index;
                performClick();
                invalidate();
                return true;
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
        if (total <= 0 || slices.isEmpty()) {
            return;
        }

        float size = Math.min(getWidth(), getHeight()) - 8f;
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        bounds.set(left, top, left + size, top + size);

        float startAngle = -90f;
        float remainingSweep = 360f * animationProgress;
        for (int index = 0; index < slices.size(); index++) {
            PieSlice slice = slices.get(index);
            float sweep = (slice.amount * 360f) / total;
            slicePaint.setColor(index == selectedIndex ? ExpenseCharts.darkenChartColor(slice.color) : slice.color);
            float visibleSweep = Math.min(sweep, Math.max(0f, remainingSweep));
            if (visibleSweep > 0f) {
                canvas.drawArc(bounds, startAngle, visibleSweep, true, slicePaint);
            }
            startAngle += sweep;
            remainingSweep -= sweep;
        }

        float holeRadius = size * 0.28f;
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, holeRadius, holePaint);
        if (selectedIndex >= 0 && selectedIndex < slices.size()) {
            PieSlice selected = slices.get(selectedIndex);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            Paint.FontMetrics metrics = centerAmountPaint.getFontMetrics();
            canvas.drawText(ExpenseDbHelper.formatMoney(selected.amount),
                    centerX, centerY - (metrics.ascent + metrics.descent) / 2f, centerAmountPaint);
        }
    }

    private int touchedSliceIndex(float x, float y) {
        if (total <= 0 || slices.isEmpty()) {
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
        for (int index = 0; index < slices.size(); index++) {
            cumulative += (slices.get(index).amount * 360f) / total;
            if (angle <= cumulative) {
                return index;
            }
        }
        return slices.size() - 1;
    }
}

class SpendingTrendChartView extends AnimatedChartView {
    private final long[] expenses;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint amountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barBounds = new RectF();
    private int selectedIndex = -1;

    SpendingTrendChartView(Context context, long[] expenses) {
        super(context);
        this.expenses = expenses;
        barPaint.setColor(ExpenseCharts.COLOR_RED);
        selectedBarPaint.setColor(ExpenseCharts.COLOR_NAVY);
        gridPaint.setColor(ExpenseCharts.COLOR_BORDER);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f, 8f}, 0f));
        textPaint.setColor(ExpenseCharts.COLOR_MUTED);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
        amountPaint.setColor(ExpenseCharts.COLOR_NAVY);
        amountPaint.setTextAlign(Paint.Align.CENTER);
        amountPaint.setTypeface(Typeface.DEFAULT_BOLD);
        amountPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
        axisPaint.setColor(ExpenseCharts.COLOR_MUTED);
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        axisPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 8f);
        setWillNotDraw(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = touchedBarIndex(event.getX(), event.getY());
            if (index >= 0) {
                selectedIndex = index;
                performClick();
                invalidate();
                return true;
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
        if (expenses == null || expenses.length == 0) {
            return;
        }

        long max = 0;
        for (long value : expenses) {
            max = Math.max(max, value);
        }
        long axisMax = ExpenseCharts.niceAxisMax(max);

        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        float leftAxis = 48f * density;
        float rightPadding = 6f * density;
        float top = 16f * density;
        float bottom = height - 22f * density;
        float chartHeight = Math.max(1f, bottom - top);
        float chartWidth = Math.max(1f, width - leftAxis - rightPadding);
        float slot = chartWidth / expenses.length;
        float barWidth = Math.max(2f * density, slot * 0.58f);

        ExpenseCharts.drawYAxis(canvas, axisPaint, gridPaint, leftAxis, width - rightPadding,
                top, chartHeight, axisMax, density);

        for (int index = 0; index < expenses.length; index++) {
            float centerX = leftAxis + slot * index + slot / 2f;
            float fullBarHeight = expenses[index] <= 0
                    ? 0
                    : Math.max(3f * density, chartHeight * expenses[index] / axisMax);
            float barHeight = fullBarHeight * animationProgress;
            barBounds.set(centerX - barWidth / 2f, bottom - barHeight, centerX + barWidth / 2f, bottom);
            float radius = ExpenseCharts.chartBarRadius(barWidth, density);
            canvas.drawRoundRect(barBounds, radius, radius,
                    index == selectedIndex ? selectedBarPaint : barPaint);
            if (index == selectedIndex) {
                float amountY = Math.max(top + 11f * density, bottom - barHeight - 6f * density);
                canvas.drawText(ExpenseDbHelper.formatMoney(expenses[index]),
                        centerX, amountY, amountPaint);
            }
            if (index == 0 || index == expenses.length - 1
                    || ((index + 1) % 5 == 0 && index < expenses.length - 2)) {
                canvas.drawText(String.valueOf(index + 1), centerX, height - 6f * density, textPaint);
            }
        }
    }

    private int touchedBarIndex(float x, float y) {
        if (expenses == null || expenses.length == 0) {
            return -1;
        }
        float density = getResources().getDisplayMetrics().density;
        float leftAxis = 48f * density;
        float right = getWidth() - 6f * density;
        float top = 16f * density;
        float bottom = getHeight() - 22f * density;
        if (x < leftAxis || x > right || y < top || y > bottom) {
            return -1;
        }
        float slot = (right - leftAxis) / expenses.length;
        int index = Math.min(expenses.length - 1, (int) ((x - leftAxis) / slot));
        return expenses[index] > 0 ? index : -1;
    }
}

class CashflowBarChartView extends AnimatedChartView {
    private final long[] income;
    private final long[] expense;
    private final Paint incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint amountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barBounds = new RectF();
    private int selectedIndex = -1;
    private int selectedSeries = -1;

    CashflowBarChartView(Context context, long[] income, long[] expense) {
        super(context);
        this.income = income;
        this.expense = expense;
        incomePaint.setColor(ExpenseCharts.COLOR_GREEN);
        expensePaint.setColor(ExpenseCharts.COLOR_RED);
        selectedBarPaint.setColor(ExpenseCharts.COLOR_NAVY);
        gridPaint.setColor(ExpenseCharts.COLOR_BORDER);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f, 8f}, 0f));
        textPaint.setColor(ExpenseCharts.COLOR_MUTED);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
        amountPaint.setColor(ExpenseCharts.COLOR_NAVY);
        amountPaint.setTextAlign(Paint.Align.CENTER);
        amountPaint.setTypeface(Typeface.DEFAULT_BOLD);
        amountPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
        axisPaint.setColor(ExpenseCharts.COLOR_MUTED);
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        axisPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 8f);
        setWillNotDraw(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int code = touchedBarCode(event.getX(), event.getY());
            if (code >= 0) {
                selectedIndex = code / 2;
                selectedSeries = code % 2;
                performClick();
                invalidate();
                return true;
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
        if (income == null || expense == null || income.length == 0) {
            return;
        }

        long max = 0;
        for (int index = 0; index < income.length; index++) {
            max = Math.max(max, income[index]);
            max = Math.max(max, expense[index]);
        }
        long axisMax = ExpenseCharts.niceAxisMax(max);

        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        float leftAxis = 48f * density;
        float rightPadding = 6f * density;
        float top = 16f * density;
        float bottom = height - 22f * density;
        float chartHeight = Math.max(1f, bottom - top);
        float chartWidth = Math.max(1f, width - leftAxis - rightPadding);
        float slot = chartWidth / income.length;
        float barWidth = Math.max(2f * density, slot * 0.24f);

        ExpenseCharts.drawYAxis(canvas, axisPaint, gridPaint, leftAxis, width - rightPadding,
                top, chartHeight, axisMax, density);

        for (int index = 0; index < income.length; index++) {
            float centerX = leftAxis + slot * index + slot / 2f;
            float incomeX = centerX - barWidth * 0.7f;
            float expenseX = centerX + barWidth * 0.7f;
            drawBar(canvas, incomeX, bottom, chartHeight, barWidth, income[index], axisMax,
                    index == selectedIndex && selectedSeries == 0 ? selectedBarPaint : incomePaint);
            drawBar(canvas, expenseX, bottom, chartHeight, barWidth, expense[index], axisMax,
                    index == selectedIndex && selectedSeries == 1 ? selectedBarPaint : expensePaint);
            if (index == selectedIndex) {
                long value = selectedSeries == 0 ? income[index] : expense[index];
                float selectedX = selectedSeries == 0 ? incomeX : expenseX;
                float fullBarHeight = value <= 0
                        ? 0
                        : Math.max(3f * density, chartHeight * value / axisMax);
                float barHeight = fullBarHeight * animationProgress;
                float amountY = Math.max(top + 11f * density, bottom - barHeight - 6f * density);
                canvas.drawText(ExpenseDbHelper.formatMoney(value), selectedX, amountY, amountPaint);
            }
            if (income.length <= 7 || index == 0 || index == income.length - 1
                    || ((index + 1) % 5 == 0 && index < income.length - 2)) {
                canvas.drawText(String.valueOf(index + 1), centerX, height - 6f * density, textPaint);
            }
        }
    }

    private void drawBar(Canvas canvas, float centerX, float bottom, float chartHeight, float barWidth,
                         long value, long max, Paint paint) {
        float density = getResources().getDisplayMetrics().density;
        float fullBarHeight = value <= 0 ? 0 : Math.max(3f * density, chartHeight * value / max);
        float barHeight = fullBarHeight * animationProgress;
        barBounds.set(centerX - barWidth / 2f, bottom - barHeight, centerX + barWidth / 2f, bottom);
        float radius = ExpenseCharts.chartBarRadius(barWidth, density);
        canvas.drawRoundRect(barBounds, radius, radius, paint);
    }

    private int touchedBarCode(float x, float y) {
        if (income == null || expense == null || income.length == 0) {
            return -1;
        }
        float density = getResources().getDisplayMetrics().density;
        float leftAxis = 48f * density;
        float right = getWidth() - 6f * density;
        float top = 16f * density;
        float bottom = getHeight() - 22f * density;
        if (x < leftAxis || x > right || y < top || y > bottom) {
            return -1;
        }

        float slot = (right - leftAxis) / income.length;
        int index = Math.min(income.length - 1, (int) ((x - leftAxis) / slot));
        float centerX = leftAxis + slot * index + slot / 2f;
        int series = x < centerX ? 0 : 1;
        if ((series == 0 ? income[index] : expense[index]) <= 0) {
            series = 1 - series;
        }
        return (series == 0 ? income[index] : expense[index]) > 0 ? index * 2 + series : -1;
    }
}

class WeeklyBarChartView extends AnimatedChartView {
    private final long[] values;
    private final String[] labels;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint amountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barBounds = new RectF();
    private int selectedIndex = -1;

    WeeklyBarChartView(Context context, long[] values, String[] labels, int accentColor) {
        super(context);
        this.values = values;
        this.labels = labels;
        barPaint.setColor(accentColor);
        selectedBarPaint.setColor(ExpenseCharts.COLOR_NAVY);
        gridPaint.setColor(Color.rgb(224, 228, 236));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f, 8f}, 0f));
        labelPaint.setColor(Color.rgb(91, 107, 123));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
        amountPaint.setColor(ExpenseCharts.COLOR_NAVY);
        amountPaint.setTextAlign(Paint.Align.CENTER);
        amountPaint.setTypeface(Typeface.DEFAULT_BOLD);
        amountPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
        axisPaint.setColor(Color.rgb(91, 107, 123));
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        axisPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 8f);
        setWillNotDraw(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = touchedBarIndex(event.getX(), event.getY());
            if (index >= 0 && index < values.length) {
                selectedIndex = index;
                performClick();
                invalidate();
                return true;
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

        long max = 0;
        for (long value : values) {
            max = Math.max(max, value);
        }
        long axisMax = ExpenseCharts.niceAxisMax(max);

        float width = getWidth();
        float height = getHeight();
        float density = getResources().getDisplayMetrics().density;
        float leftAxis = 46f * density;
        float rightPadding = 6f * density;
        float top = 16f * density;
        float labelArea = 28f * density;
        float bottomPadding = 8f * density;
        float chartHeight = Math.max(1f, height - labelArea - top - bottomPadding);
        float chartWidth = Math.max(1f, width - leftAxis - rightPadding);
        float slot = chartWidth / values.length;
        float barWidth = Math.min(slot * 0.56f, 36f * density);
        float radius = ExpenseCharts.chartBarRadius(barWidth, density);

        ExpenseCharts.drawYAxis(canvas, axisPaint, gridPaint, leftAxis, width - rightPadding,
                top, chartHeight, axisMax, density);

        for (int index = 0; index < values.length; index++) {
            float centerX = leftAxis + slot * index + slot / 2f;
            float trackLeft = centerX - barWidth / 2f;
            float trackBottom = top + chartHeight;

            float fullHeight = Math.max(values[index] <= 0 ? 0f : 8f,
                    chartHeight * values[index] / axisMax);
            float fillHeight = fullHeight * animationProgress;
            float fillTop = trackBottom - fillHeight;
            barBounds.set(trackLeft, fillTop, trackLeft + barWidth, trackBottom);
            canvas.drawRoundRect(barBounds, radius, radius,
                    index == selectedIndex ? selectedBarPaint : barPaint);

            if (index == selectedIndex) {
                float amountY = Math.max(top + 11f * density, fillTop - 6f * density);
                canvas.drawText(ExpenseDbHelper.formatMoney(values[index]), centerX, amountY, amountPaint);
            }

            if (labels != null && index < labels.length) {
                canvas.drawText(labels[index], centerX, height - 13f * density, labelPaint);
            }
        }
    }

    private int touchedBarIndex(float x, float y) {
        if (values == null || values.length == 0) {
            return -1;
        }

        long max = 0;
        for (long value : values) {
            max = Math.max(max, value);
        }
        if (max <= 0) {
            return -1;
        }
        long axisMax = ExpenseCharts.niceAxisMax(max);

        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        float leftAxis = 46f * density;
        float rightPadding = 6f * density;
        float top = 16f * density;
        float labelArea = 28f * density;
        float bottomPadding = 8f * density;
        float chartHeight = Math.max(1f, height - labelArea - top - bottomPadding);
        float chartWidth = Math.max(1f, width - leftAxis - rightPadding);
        float slot = chartWidth / values.length;
        float barWidth = Math.min(slot * 0.56f, 36f * density);
        float trackBottom = top + chartHeight;

        for (int index = 0; index < values.length; index++) {
            float centerX = leftAxis + slot * index + slot / 2f;
            float trackLeft = centerX - barWidth / 2f;
            float fillHeight = Math.max(values[index] <= 0 ? 0f : 8f, chartHeight * values[index] / axisMax);
            float fillTop = trackBottom - fillHeight;
            if (x >= trackLeft && x <= trackLeft + barWidth && y >= fillTop && y <= trackBottom) {
                return index;
            }
        }
        return -1;
    }
}
