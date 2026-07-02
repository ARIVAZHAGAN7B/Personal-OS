package com.ariva.personalos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AppUi {
    private final Context context;

    public AppUi(Context context) {
        this.context = context;
    }

    public TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.0f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    public TextView sectionTitle(String title) {
        TextView view = text(title, 20, Color.rgb(16, 24, 40), true);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    public TextView metricCard(String label, String value) {
        TextView view = text(label + "\n" + value, 18, Color.WHITE, true);
        view.setBackgroundColor(Color.rgb(22, 63, 95));
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setMinHeight(dp(92));
        return view;
    }

    public LinearLayout compactMetricRow(String label, String value, int valueColor) {
        LinearLayout row = horizontalRow();
        TextView left = text(label, 14, Color.rgb(52, 64, 84), true);
        TextView right = text(value, 14, valueColor, true);
        right.setGravity(Gravity.RIGHT);
        row.addView(left, weightParams());
        row.addView(right, weightParams());
        return row;
    }

    public LinearLayout progressBar(long value, long max, int color) {
        int filledWeight = max <= 0 ? 0 : Math.max(4, Math.round((value * 100f) / max));
        return new AnimatedProgressBar(
                context,
                Math.min(100, filledWeight),
                color,
                dp(8),
                tileBackground(Color.rgb(234, 236, 240), Color.TRANSPARENT));
    }

    public Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.rgb(0, 110, 130));
        button.setPadding(dp(8), dp(10), dp(8), dp(10));
        button.setOnClickListener(listener);
        return button;
    }

    public LinearLayout panel() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(tileBackground(Color.WHITE, Color.TRANSPARENT));
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setMinimumHeight(dp(48));
        return panel;
    }

    public LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return row;
    }

    public LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    public void addSpace(LinearLayout layout, int dp) {
        View spacer = new View(context);
        layout.addView(spacer, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    public int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public GradientDrawable tileBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(8));
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }
}
