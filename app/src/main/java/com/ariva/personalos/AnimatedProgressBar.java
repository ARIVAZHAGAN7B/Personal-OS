package com.ariva.personalos;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

class AnimatedProgressBar extends LinearLayout implements ViewTreeObserver.OnScrollChangedListener {
    private final View filled;
    private final View empty;
    private final LayoutParams filledParams;
    private final LayoutParams emptyParams;
    private final float targetWeight;
    private final Rect visibleBounds = new Rect();
    private ViewTreeObserver registeredObserver;
    private ValueAnimator animator;
    private boolean started;

    AnimatedProgressBar(Context context, int targetWeight, int color, int heightPx, Drawable background) {
        super(context);
        this.targetWeight = Math.max(0, Math.min(100, targetWeight));
        setOrientation(HORIZONTAL);
        setBackground(background);

        filled = new View(context);
        filled.setBackgroundColor(color);
        empty = new View(context);
        filledParams = new LayoutParams(0, heightPx, 0f);
        emptyParams = new LayoutParams(0, heightPx, 100f);
        addView(filled, filledParams);
        addView(empty, emptyParams);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (targetWeight <= 0 || started) {
            return;
        }
        registeredObserver = getViewTreeObserver();
        registeredObserver.addOnScrollChangedListener(this);
        post(this::startIfVisible);
    }

    @Override
    public void onScrollChanged() {
        startIfVisible();
    }

    private void startIfVisible() {
        if (started || getHeight() <= 0 || !getGlobalVisibleRect(visibleBounds)) {
            return;
        }
        if (visibleBounds.height() < getHeight() * 0.3f
                || visibleBounds.width() < getWidth() * 0.3f) {
            return;
        }

        started = true;
        removeScrollListener();
        animator = ValueAnimator.ofFloat(0f, targetWeight);
        animator.setDuration(650L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float animatedWeight = (float) animation.getAnimatedValue();
            filledParams.weight = animatedWeight;
            emptyParams.weight = 100f - animatedWeight;
            filled.setLayoutParams(filledParams);
            empty.setLayoutParams(emptyParams);
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeScrollListener();
        if (animator != null) {
            animator.cancel();
            animator = null;
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
