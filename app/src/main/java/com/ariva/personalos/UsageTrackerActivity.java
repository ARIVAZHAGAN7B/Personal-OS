package com.ariva.personalos;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

public class UsageTrackerActivity extends Activity {
    static final String EXTRA_PACKAGE_NAME = "package_name";
    static final String EXTRA_APP_NAME = "app_name";
    static final String EXTRA_CATEGORY = "category";
    static final String EXTRA_DATE_START = "date_start";
    private static final String PAGE_HOME = "home";
    private static final String PAGE_ANALYTICS = "analytics";
    private static final String PAGE_SETTINGS = "settings";
    private static final String PAGE_APP_DETAIL = "app_detail";
    private static final String PAGE_DAY_DETAIL = "day_detail";
    private static final int COLOR_NAVY_TEXT = Color.rgb(13, 34, 54);
    private static final int COLOR_MUTED = Color.rgb(91, 107, 123);
    private static final int COLOR_TEAL = Color.rgb(0, 110, 130);
    private static final int COLOR_BORDER = Color.rgb(224, 228, 236);
    private static final int COLOR_GREEN = Color.rgb(15, 157, 88);
    private static final int COLOR_RED = Color.rgb(180, 35, 24);
    private static final int[] CHART_COLORS = new int[]{
            COLOR_TEAL,
            Color.rgb(22, 63, 95),
            COLOR_GREEN,
            Color.rgb(245, 158, 11),
            COLOR_RED,
            Color.rgb(99, 102, 241),
            Color.rgb(219, 39, 119)
    };
    private static final String TAB_TODAY = "Today";
    private static final String TAB_WEEK = "Weekly";
    private static final String TAB_MONTH = "Monthly";

    private UsageDbHelper db;
    private AppUi ui;
    private LinearLayout content;
    private String selectedTab = TAB_TODAY;
    private String selectedPackageName = null;
    private String selectedAppName = null;
    private String selectedCategory = null;
    private boolean syncInProgress = false;
    private boolean hideSystemApps = true;
    private boolean destroyed = false;
    private long selectedDayStart = 0;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new UsageDbHelper(this);
        ui = new AppUi(this);
        hideSystemApps = preferences().getBoolean("hide_system_apps", true);
        if (PAGE_APP_DETAIL.equals(pageType())) {
            selectedPackageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
            selectedAppName = getIntent().getStringExtra(EXTRA_APP_NAME);
            selectedCategory = getIntent().getStringExtra(EXTRA_CATEGORY);
        }
        if (PAGE_DAY_DETAIL.equals(pageType()) || PAGE_APP_DETAIL.equals(pageType())) {
            selectedDayStart = getIntent().getLongExtra(EXTRA_DATE_START, 0);
        }
        UsageSyncScheduler.schedule(this);
        render();
        startSync(60, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UsageTracker.hasUsageAccess(this)) {
            startSync(60, false);
        }
        render();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        db.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    protected String pageType() {
        return PAGE_HOME;
    }

    private void render() {
        boolean detailPage = PAGE_APP_DETAIL.equals(pageType()) || PAGE_DAY_DETAIL.equals(pageType());
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(247, 249, 252));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 249, 252));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (!detailPage) {
            screen.addView(bottomNavigation(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(68)));
        }

        content.addView(mainPageBack());
        content.addView(ui.text(pageTitle(), 20, COLOR_NAVY_TEXT, true));
        content.addView(ui.text(new SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(new Date()),
                12, COLOR_MUTED, false));
        ui.addSpace(content, 14);

        if (detailPage) {
            if (!UsageTracker.hasUsageAccess(this)) {
                renderPermissionPanel();
                setContentView(screen);
                return;
            }
            if (PAGE_DAY_DETAIL.equals(pageType())) {
                renderDayDetailPage();
            } else {
                renderAppDetailPage();
            }
            setContentView(screen);
            return;
        }

        if (!UsageTracker.hasUsageAccess(this) && !PAGE_SETTINGS.equals(pageType())) {
            renderPermissionPanel();
            setContentView(screen);
            return;
        }
        if (PAGE_SETTINGS.equals(pageType())) {
            renderActions();
        } else if (PAGE_ANALYTICS.equals(pageType())) {
            renderAnalyticsPage();
        } else {
            renderHomePage();
        }
        setContentView(screen);
    }

    private String pageTitle() {
        if (PAGE_ANALYTICS.equals(pageType())) return "Usage analytics";
        if (PAGE_SETTINGS.equals(pageType())) return "Tracker settings";
        if (PAGE_APP_DETAIL.equals(pageType())) return "App details";
        if (PAGE_DAY_DETAIL.equals(pageType())) return "Daily usage details";
        return "Digital tracker";
    }

    private void renderHomePage() {
        selectedTab = TAB_TODAY;
        UsageRange today = UsageRange.today();
        renderSummary(today);
        ui.addSpace(content, 14);
        renderTopApps(today);
        ui.addSpace(content, 14);
        renderFrequentOpenWarnings(today);
        ui.addSpace(content, 14);
        renderHourlyBreakdown(today.start);
        ui.addSpace(content, 14);
        renderCategoryBreakdown(today);
    }

    private void renderAnalyticsPage() {
        selectedTab = TAB_WEEK;
        content.addView(ui.text("This week", 18, COLOR_NAVY_TEXT, true));
        ui.addSpace(content, 8);
        UsageRange week = UsageRange.thisWeek();
        renderSummary(week);
        ui.addSpace(content, 14);
        renderUsageBarChart(week, false);
        ui.addSpace(content, 14);
        renderCategoryPieChart(week);
        ui.addSpace(content, 14);
        renderBestWorstDays(week);
        ui.addSpace(content, 22);

        selectedTab = TAB_MONTH;
        content.addView(ui.text("This month", 18, COLOR_NAVY_TEXT, true));
        ui.addSpace(content, 8);
        UsageRange month = UsageRange.thisMonth();
        renderSummary(month);
        ui.addSpace(content, 14);
        renderUsageBarChart(month, true);
        ui.addSpace(content, 14);
        renderCategoryPieChart(month);
        ui.addSpace(content, 14);
        renderBestWorstDays(month);
    }

    private void renderUsageBarChart(UsageRange range, boolean monthly) {
        int days = (int) range.dayCount();
        long[] values = new long[days];
        String[] labels = new String[days];

        Cursor cursor = db.getDailyUsageTotals(range.start, range.end);
        try {
            while (cursor.moveToNext()) {
                long dateStart = cursor.getLong(cursor.getColumnIndexOrThrow("date_start"));
                int index = (int) ((dateStart - range.start) / UsageRange.DAY_MS);
                if (index >= 0 && index < days) {
                    values[index] = cursor.getLong(cursor.getColumnIndexOrThrow("total_ms"));
                }
            }
        } finally {
            cursor.close();
        }

        SimpleDateFormat weeklyLabel = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int index = 0; index < days; index++) {
            if (monthly) {
                int dayNumber = index + 1;
                labels[index] = dayNumber == 1 || dayNumber == days || dayNumber % 5 == 0
                        ? String.valueOf(dayNumber)
                        : "";
            } else {
                labels[index] = weeklyLabel.format(new Date(range.start + index * UsageRange.DAY_MS));
            }
        }

        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle(monthly ? "Daily usage this month" : "Daily usage this week"));
        panel.addView(ui.text("Tap a bar to open that day's usage details.", 12, COLOR_MUTED, false));
        ui.addSpace(panel, 10);
        UsageBarChartView chart = new UsageBarChartView(
                this, values, labels, COLOR_TEAL, COLOR_NAVY_TEXT, index -> {
                    long dayStart = range.start + index * UsageRange.DAY_MS;
                    Intent detail = new Intent(
                            UsageTrackerActivity.this, UsageDayDetailActivity.class);
                    detail.putExtra(EXTRA_DATE_START, dayStart);
                    startActivity(detail);
                });
        chart.setBackground(ui.tileBackground(Color.WHITE, COLOR_BORDER));
        panel.addView(chart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(220)));
        content.addView(panel);
    }

    private void renderCategoryPieChart(UsageRange range) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        long total = 0;
        Cursor cursor = db.getCategoryUsage(range.start, range.end);
        try {
            int index = 0;
            while (cursor.moveToNext()) {
                long value = cursor.getLong(cursor.getColumnIndexOrThrow("total_ms"));
                if (value <= 0) {
                    continue;
                }
                labels.add(cursor.getString(cursor.getColumnIndexOrThrow("category")));
                values.add(value);
                colors.add(CHART_COLORS[index % CHART_COLORS.length]);
                total += value;
                index++;
            }
        } finally {
            cursor.close();
        }

        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Categories"));
        if (values.isEmpty()) {
            panel.addView(ui.text("No category data yet.", 14, COLOR_MUTED, false));
            content.addView(panel);
            return;
        }

        long[] chartValues = new long[values.size()];
        int[] chartColors = new int[colors.size()];
        String[] chartLabels = labels.toArray(new String[0]);
        for (int index = 0; index < values.size(); index++) {
            chartValues[index] = values.get(index);
            chartColors[index] = colors.get(index);
        }

        LinearLayout chartRow = ui.horizontalRow();
        chartRow.setGravity(Gravity.CENTER_VERTICAL);
        UsagePieChartView chart = new UsagePieChartView(
                this, chartLabels, chartValues, chartColors, COLOR_NAVY_TEXT);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(ui.dp(120), ui.dp(120));
        chartParams.setMargins(0, 0, ui.dp(12), 0);
        chartRow.addView(chart, chartParams);

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < labels.size(); index++) {
            legend.addView(categoryLegendRow(
                    labels.get(index), values.get(index), colors.get(index), total));
        }
        chartRow.addView(legend, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(chartRow);
        panel.addView(ui.text("Tap a slice to see exact screen time.", 12, COLOR_MUTED, false));
        content.addView(panel);
    }

    private LinearLayout categoryLegendRow(String label, long value, int color, long total) {
        LinearLayout row = ui.horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);

        View swatch = new View(this);
        swatch.setBackground(ui.tileBackground(color, Color.TRANSPARENT));
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(ui.dp(10), ui.dp(10));
        swatchParams.setMargins(0, 0, ui.dp(8), 0);
        row.addView(swatch, swatchParams);

        TextView name = ui.text(label, 11, COLOR_MUTED, false);
        TextView percentage = ui.text(percentText(value, total), 11, COLOR_NAVY_TEXT, true);
        percentage.setGravity(Gravity.RIGHT);
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(percentage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void renderAppDetailPage() {
        if (selectedPackageName == null || selectedPackageName.trim().isEmpty()) {
            content.addView(ui.text("App details are unavailable.", 14, COLOR_MUTED, false));
            return;
        }
        renderAppDetailHeader();
        ui.addSpace(content, 14);

        selectedTab = TAB_TODAY;
        UsageRange detailDay = selectedDayStart > 0
                ? new UsageRange(selectedDayStart, selectedDayStart + UsageRange.DAY_MS)
                : UsageRange.today();
        String detailDayLabel = selectedDayStart > 0
                ? new SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
                        .format(new Date(selectedDayStart))
                : "Today";
        content.addView(ui.text(detailDayLabel, 18, COLOR_NAVY_TEXT, true));
        ui.addSpace(content, 8);
        renderAppDetailSummary(detailDay);
        ui.addSpace(content, 14);
        renderUsageLimitControls();
        ui.addSpace(content, 22);

        selectedTab = TAB_WEEK;
        content.addView(ui.text("This week", 18, COLOR_NAVY_TEXT, true));
        ui.addSpace(content, 8);
        renderAppDetailSummary(UsageRange.thisWeek());
        ui.addSpace(content, 14);
        renderAppTrend(UsageRange.thisWeek());
        ui.addSpace(content, 22);

        selectedTab = TAB_MONTH;
        content.addView(ui.text("This month", 18, COLOR_NAVY_TEXT, true));
        ui.addSpace(content, 8);
        renderAppDetailSummary(UsageRange.thisMonth());
        ui.addSpace(content, 14);
        renderAppTrend(UsageRange.thisMonth());
    }

    private void renderDayDetailPage() {
        if (selectedDayStart <= 0) {
            content.addView(ui.text("Daily usage details are unavailable.", 14, COLOR_MUTED, false));
            return;
        }
        UsageRange day = new UsageRange(selectedDayStart, selectedDayStart + UsageRange.DAY_MS);
        selectedTab = TAB_TODAY;

        LinearLayout datePanel = ui.panel();
        datePanel.addView(ui.text(
                new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date(selectedDayStart)),
                18, COLOR_NAVY_TEXT, true));
        datePanel.addView(ui.text(
                new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date(selectedDayStart)),
                13, COLOR_MUTED, false));
        content.addView(datePanel);
        ui.addSpace(content, 14);
        renderSummary(day);
        ui.addSpace(content, 14);
        renderTopApps(day);
        ui.addSpace(content, 14);
        renderFrequentOpenWarnings(day);
        ui.addSpace(content, 14);
        renderHourlyBreakdown(day.start);
        ui.addSpace(content, 14);
        renderCategoryBreakdown(day);
    }

    private LinearLayout bottomNavigation() {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(ui.dp(6), ui.dp(4), ui.dp(6), ui.dp(4));
        navigation.setBackground(ui.tileBackground(Color.WHITE, COLOR_BORDER));
        navigation.setElevation(ui.dp(8));
        navigation.addView(navItem(
                "Today", R.drawable.ic_nav_home, PAGE_HOME, UsageTrackerActivity.class), navItemParams());
        navigation.addView(navItem(
                "Analytics", R.drawable.ic_nav_analytics, PAGE_ANALYTICS, UsageAnalyticsActivity.class), navItemParams());
        navigation.addView(navItem(
                "Settings", R.drawable.ic_nav_settings, PAGE_SETTINGS, UsageSettingsActivity.class), navItemParams());
        return navigation;
    }

    private TextView navItem(String label, int iconResource, String page,
                             final Class<? extends Activity> destination) {
        boolean selected = page.equals(pageType());
        int itemColor = selected ? COLOR_TEAL : COLOR_MUTED;
        TextView item = ui.text(label, 11, itemColor, selected);
        item.setGravity(Gravity.CENTER);
        item.setCompoundDrawablesWithIntrinsicBounds(0, iconResource, 0, 0);
        item.setCompoundDrawableTintList(ColorStateList.valueOf(itemColor));
        item.setCompoundDrawablePadding(ui.dp(3));
        item.setBackground(ui.tileBackground(
                selected ? Color.rgb(229, 244, 246) : Color.TRANSPARENT,
                Color.TRANSPARENT));
        item.setContentDescription(label + (selected ? ", selected" : ""));
        item.setEnabled(!selected);
        item.setOnClickListener(v -> {
            startActivity(new Intent(UsageTrackerActivity.this, destination));
            finish();
        });
        return item;
    }

    private LinearLayout.LayoutParams navItemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(ui.dp(3), 0, ui.dp(3), 0);
        return params;
    }

    private TextView mainPageBack() {
        TextView back = ui.text("<", 24, Color.rgb(0, 110, 130), true);
        back.setGravity(Gravity.LEFT);
        back.setPadding(0, 0, 0, ui.dp(2));
        back.setMinHeight(ui.dp(32));
        back.setContentDescription("Back to main page");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        return back;
    }

    private void renderPermissionPanel() {
        LinearLayout panel = ui.panel();
        panel.addView(ui.text("Usage access is required", 20, Color.rgb(16, 24, 40), true));
        panel.addView(ui.text("Android requires you to enable this manually. After enabling Personal OS, return here and the tracker will start saving app usage history.",
                14, Color.rgb(71, 84, 103), false));
        ui.addSpace(panel, 12);
        panel.addView(ui.actionButton("Open Usage Access Settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            }
        }));
        content.addView(panel);
    }

    private void renderActions() {
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Data and privacy"));
        LinearLayout row = ui.horizontalRow();
        Button sync = ui.actionButton(syncInProgress ? "Syncing..." : "Sync", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSync(60, true);
            }
        });
        sync.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_popup_sync, 0, 0, 0);
        sync.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        sync.setCompoundDrawablePadding(ui.dp(6));
        sync.setEnabled(!syncInProgress);
        row.addView(sync, ui.weightParams());

        Button settings = ui.actionButton("Usage access", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            }
        });
        settings.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_preferences, 0, 0, 0);
        settings.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        settings.setCompoundDrawablePadding(ui.dp(6));
        row.addView(settings, ui.weightParams());
        panel.addView(row);

        ui.addSpace(panel, 10);
        Switch systemApps = new Switch(this);
        systemApps.setText("Hide system apps");
        systemApps.setTextSize(14);
        systemApps.setTextColor(COLOR_NAVY_TEXT);
        systemApps.setChecked(hideSystemApps);
        systemApps.setOnCheckedChangeListener((buttonView, checked) -> {
            hideSystemApps = checked;
            preferences().edit().putBoolean("hide_system_apps", checked).apply();
            render();
        });
        panel.addView(systemApps);

        long lastSync = db.getLastSyncTime();
        if (lastSync > 0) {
            ui.addSpace(panel, 6);
            panel.addView(ui.text("Last sync  " + dateTimeFormat.format(new Date(lastSync)),
                    12, COLOR_MUTED, false));
        } else if (syncInProgress) {
            ui.addSpace(panel, 6);
            panel.addView(ui.text("Syncing usage...", 12, COLOR_MUTED, false));
        }
        content.addView(panel);
    }

    private void renderSummary(UsageRange range) {
        Map<String, Long> summary = db.getSummary(range.start, range.end);
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle(selectedTab + " Summary"));
        LinearLayout row = ui.horizontalRow();
        row.addView(summaryMetric("Screen time", formatDuration(summary.get("total_ms")), COLOR_TEAL), ui.weightParams());
        row.addView(summaryMetric("Opens", String.valueOf(summary.get("launches")), COLOR_NAVY_TEXT), ui.weightParams());
        panel.addView(row);
        ui.addSpace(panel, 8);
        row = ui.horizontalRow();
        row.addView(summaryMetric("Apps used", String.valueOf(summary.get("app_count")), COLOR_GREEN), ui.weightParams());
        row.addView(summaryMetric("Daily average",
                formatDuration(summary.get("total_ms") / range.dayCount()), COLOR_TEAL), ui.weightParams());
        panel.addView(row);
        content.addView(panel);
    }

    private LinearLayout summaryMetric(String label, String value, int valueColor) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(4));
        metric.setMinimumHeight(ui.dp(52));
        metric.addView(ui.text(label, 12, COLOR_MUTED, false));
        TextView valueView = ui.text(value, 17, valueColor, true);
        valueView.setPadding(0, ui.dp(4), 0, 0);
        metric.addView(valueView);
        return metric;
    }

    private void renderTopApps(UsageRange range) {
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Most Used Apps"));
        Cursor cursor = db.getTopApps(range.start, range.end, 50);
        int shown = 0;
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(ui.text("No app usage captured yet. Tap Sync Now after granting usage access.",
                        14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    String packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name"));
                    String appName = cursor.getString(cursor.getColumnIndexOrThrow("app_name"));
                    String category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
                    long totalMs = cursor.getLong(cursor.getColumnIndexOrThrow("total_ms"));
                    long launches = cursor.getLong(cursor.getColumnIndexOrThrow("launches"));
                    long firstOpen = cursor.getLong(cursor.getColumnIndexOrThrow("first_open_time"));
                    long lastOpen = cursor.getLong(cursor.getColumnIndexOrThrow("last_open_time"));
                    boolean installed = cursor.getInt(cursor.getColumnIndexOrThrow("is_installed")) == 1 && isInstalled(packageName);
                    db.markInstalled(packageName, installed);
                    if (hideSystemApps && isSystemApp(packageName)) {
                        continue;
                    }
                    panel.addView(appUsageRow(packageName, appName, category, totalMs, launches, firstOpen, lastOpen, installed));
                    ui.addSpace(panel, 10);
                    shown++;
                } while (cursor.moveToNext());
                if (shown == 0) {
                    panel.addView(ui.text("Only hidden system app usage exists in this range.",
                            14, Color.rgb(102, 112, 133), false));
                }
            }
        } finally {
            cursor.close();
        }
        content.addView(panel);
    }

    private LinearLayout appUsageRow(String packageName, String appName, String category, long totalMs,
                                     long launches, long firstOpen, long lastOpen, boolean installed) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(6), 0, ui.dp(6));

        String displayName = loadAppLabel(packageName, appName);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(loadAppIcon(packageName));
        icon.setContentDescription(displayName);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44));
        iconParams.setMargins(0, 0, ui.dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView name = ui.text(displayName, 15, Color.rgb(16, 24, 40), true);
        TextView details = ui.text(appDetails(category, launches, firstOpen, lastOpen, installed),
                12, Color.rgb(71, 84, 103), false);
        textColumn.addView(name);
        textColumn.addView(details);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView duration = ui.text(formatDuration(totalMs), 15, Color.rgb(0, 110, 130), true);
        duration.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        durationParams.setMargins(ui.dp(10), 0, 0, 0);
        row.addView(duration, durationParams);
        row.setBackground(ui.tileBackground(Color.WHITE, Color.TRANSPARENT));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent detail = new Intent(UsageTrackerActivity.this, UsageAppDetailActivity.class);
                detail.putExtra(EXTRA_PACKAGE_NAME, packageName);
                detail.putExtra(EXTRA_APP_NAME, displayName);
                detail.putExtra(EXTRA_CATEGORY, installed ? category : category + " | Uninstalled");
                if (PAGE_DAY_DETAIL.equals(pageType()) && selectedDayStart > 0) {
                    detail.putExtra(EXTRA_DATE_START, selectedDayStart);
                }
                startActivity(detail);
            }
        });

        return row;
    }

    private void renderAppDetailHeader() {
        LinearLayout header = ui.panel();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(loadAppIcon(selectedPackageName));
        icon.setContentDescription(selectedAppName);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(ui.dp(54), ui.dp(54));
        iconParams.setMargins(0, 0, ui.dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(ui.text(selectedAppName, 18, COLOR_NAVY_TEXT, true));
        titles.addView(ui.text(selectedCategory == null ? "App usage details" : selectedCategory,
                12, COLOR_MUTED, false));
        row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(row);
        content.addView(header);
    }

    private void renderAppDetailSummary(UsageRange range) {
        Cursor cursor = db.getAppUsageSummary(selectedPackageName, range.start, range.end);
        long totalMs = 0;
        long launches = 0;
        long firstOpen = 0;
        long lastOpen = 0;
        long activeDays = 0;
        try {
            if (cursor.moveToFirst()) {
                totalMs = cursor.getLong(cursor.getColumnIndexOrThrow("total_ms"));
                launches = cursor.getLong(cursor.getColumnIndexOrThrow("launches"));
                firstOpen = cursor.getLong(cursor.getColumnIndexOrThrow("first_open_time"));
                lastOpen = cursor.getLong(cursor.getColumnIndexOrThrow("last_open_time"));
                activeDays = cursor.getLong(cursor.getColumnIndexOrThrow("active_days"));
            }
        } finally {
            cursor.close();
        }

        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Usage Summary"));
        LinearLayout row = ui.horizontalRow();
        row.addView(summaryMetric("Used", formatDuration(totalMs), COLOR_TEAL), ui.weightParams());
        row.addView(summaryMetric("Opened", String.valueOf(launches), COLOR_NAVY_TEXT), ui.weightParams());
        panel.addView(row);
        ui.addSpace(panel, 8);
        row = ui.horizontalRow();
        row.addView(summaryMetric("Daily average",
                formatDuration(totalMs / Math.max(1, range.dayCount())), COLOR_TEAL), ui.weightParams());
        row.addView(summaryMetric("Active days", activeDays + "/" + range.dayCount(), COLOR_GREEN), ui.weightParams());
        panel.addView(row);
        ui.addSpace(panel, 8);
        panel.addView(ui.compactMetricRow("First open",
                firstOpen > 0 ? dateTimeFormat.format(new Date(firstOpen)) : "No data", COLOR_MUTED));
        panel.addView(ui.compactMetricRow("Last open",
                lastOpen > 0 ? dateTimeFormat.format(new Date(lastOpen)) : "No data", COLOR_MUTED));
        content.addView(panel);
    }

    private void renderUsageLimitControls() {
        long dailyLimit = db.getDailyLimit(selectedPackageName);
        long usedToday = appTotalForRange(selectedPackageName, UsageRange.today());
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Daily Limit"));
        if (dailyLimit <= 0) {
            panel.addView(ui.text("No daily limit set. Choose a preset or set a custom limit.",
                    13, Color.rgb(71, 84, 103), false));
        } else {
            boolean overLimit = usedToday > dailyLimit;
            panel.addView(ui.text(formatDuration(usedToday) + " of " + formatDuration(dailyLimit) + " today",
                    14, overLimit ? Color.rgb(180, 35, 24) : Color.rgb(2, 122, 72), true));
        }
        ui.addSpace(panel, 8);

        LinearLayout row = ui.horizontalRow();
        row.addView(limitButton("30m", 30L * 60000L), ui.weightParams());
        row.addView(limitButton("1h", 60L * 60000L), ui.weightParams());
        row.addView(limitButton("2h", 120L * 60000L), ui.weightParams());
        panel.addView(row);
        ui.addSpace(panel, 8);
        row = ui.horizontalRow();
        row.addView(limitButton("4h", 240L * 60000L), ui.weightParams());
        row.addView(limitButton("Clear", 0), ui.weightParams());
        Button custom = ui.actionButton("Custom...", v -> showCustomLimitDialog());
        custom.setTextColor(COLOR_NAVY_TEXT);
        custom.setBackground(ui.tileBackground(Color.WHITE, COLOR_BORDER));
        row.addView(custom, ui.weightParams());
        panel.addView(row);
        content.addView(panel);
    }

    private void showCustomLimitDialog() {
        android.widget.EditText hoursInput = new android.widget.EditText(this);
        hoursInput.setHint("Hours (e.g. 3)");
        hoursInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        hoursInput.setTextSize(14);
        hoursInput.setTextColor(COLOR_NAVY_TEXT);
        hoursInput.setBackground(ui.tileBackground(Color.rgb(250, 252, 254), COLOR_BORDER));
        hoursInput.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        hoursInput.setMinHeight(ui.dp(44));

        android.widget.EditText minutesInput = new android.widget.EditText(this);
        minutesInput.setHint("Minutes (e.g. 30)");
        minutesInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutesInput.setTextSize(14);
        minutesInput.setTextColor(COLOR_NAVY_TEXT);
        minutesInput.setBackground(ui.tileBackground(Color.rgb(250, 252, 254), COLOR_BORDER));
        minutesInput.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        minutesInput.setMinHeight(ui.dp(44));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(4));
        form.addView(ui.text("Hours", 11, COLOR_MUTED, true));
        form.addView(hoursInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ui.addSpace(form, 8);
        form.addView(ui.text("Minutes", 11, COLOR_MUTED, true));
        form.addView(minutesInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new android.app.AlertDialog.Builder(this)
                .setTitle("Set Custom Daily Limit")
                .setView(form)
                .setPositiveButton("Set limit", (dialog, which) -> {
                    String h = hoursInput.getText().toString().trim();
                    String m = minutesInput.getText().toString().trim();
                    long hours = h.isEmpty() ? 0 : Long.parseLong(h);
                    long minutes = m.isEmpty() ? 0 : Long.parseLong(m);
                    long limitMs = (hours * 60L + minutes) * 60000L;
                    if (limitMs <= 0) {
                        Toast.makeText(this, "Please enter a valid limit.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.setDailyLimit(selectedPackageName, limitMs);
                    Toast.makeText(this, "Daily limit set to " + formatDuration(limitMs),
                            Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Button limitButton(String label, final long limitMs) {
        return ui.actionButton(label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                db.setDailyLimit(selectedPackageName, limitMs);
                Toast.makeText(UsageTrackerActivity.this,
                        limitMs <= 0 ? "Limit cleared" : "Daily limit set",
                        Toast.LENGTH_SHORT).show();
                render();
            }
        });
    }

    private void renderAppTrend(UsageRange range) {
        if (TAB_MONTH.equals(selectedTab)) {
            renderMonthlyWeeklyTrend(range);
            return;
        }
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle(TAB_WEEK.equals(selectedTab) ? "Weekly Trend" : "Daily Trend"));
        int days = (int) Math.max(1, range.dayCount());
        long[] totals = new long[days];
        long[] launches = new long[days];
        long max = 0;

        Cursor cursor = db.getDailyUsageForApp(selectedPackageName, range.start, range.end);
        try {
            while (cursor.moveToNext()) {
                long dateStart = cursor.getLong(cursor.getColumnIndexOrThrow("date_start"));
                int index = (int) ((dateStart - range.start) / UsageRange.DAY_MS);
                if (index >= 0 && index < days) {
                    totals[index] = cursor.getLong(cursor.getColumnIndexOrThrow("total_foreground_ms"));
                    launches[index] = cursor.getLong(cursor.getColumnIndexOrThrow("launch_count"));
                    max = Math.max(max, totals[index]);
                }
            }
        } finally {
            cursor.close();
        }

        for (int index = 0; index < days; index++) {
            long dayStart = range.start + index * UsageRange.DAY_MS;
            panel.addView(trendRow(dayStart, totals[index], launches[index], max));
            if (index < days - 1) {
                ui.addSpace(panel, 8);
            }
        }
        content.addView(panel);
    }

    private void renderMonthlyWeeklyTrend(UsageRange range) {
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Weekly Usage"));
        int weekCount = (int) Math.max(1, Math.ceil(range.dayCount() / 7.0));
        long[] totals = new long[weekCount];
        long[] launches = new long[weekCount];
        long max = 0;

        Cursor cursor = db.getDailyUsageForApp(selectedPackageName, range.start, range.end);
        try {
            while (cursor.moveToNext()) {
                long dateStart = cursor.getLong(cursor.getColumnIndexOrThrow("date_start"));
                int weekIndex = (int) ((dateStart - range.start) / (7L * UsageRange.DAY_MS));
                if (weekIndex >= 0 && weekIndex < weekCount) {
                    totals[weekIndex] += cursor.getLong(cursor.getColumnIndexOrThrow("total_foreground_ms"));
                    launches[weekIndex] += cursor.getLong(cursor.getColumnIndexOrThrow("launch_count"));
                    max = Math.max(max, totals[weekIndex]);
                }
            }
        } finally {
            cursor.close();
        }

        for (int index = 0; index < weekCount; index++) {
            long weekStart = range.start + index * 7L * UsageRange.DAY_MS;
            long weekEnd = Math.min(range.end, weekStart + 7L * UsageRange.DAY_MS);
            panel.addView(weeklyTrendRow(index + 1, weekStart, weekEnd, totals[index], launches[index], max));
            if (index < weekCount - 1) {
                ui.addSpace(panel, 8);
            }
        }
        content.addView(panel);
    }

    private LinearLayout trendRow(long dayStart, long totalMs, long launches, long max) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout labelRow = ui.horizontalRow();
        TextView day = ui.text(dayFormat.format(new Date(dayStart)), 13, Color.rgb(52, 64, 84), true);
        TextView value = ui.text(formatDuration(totalMs) + " | " + launches + (launches == 1 ? " open" : " opens"),
                13, Color.rgb(0, 110, 130), true);
        value.setGravity(Gravity.RIGHT);
        labelRow.addView(day, ui.weightParams());
        labelRow.addView(value, ui.weightParams());
        row.addView(labelRow);
        row.addView(ui.progressBar(totalMs, max, Color.rgb(0, 110, 130)));
        return row;
    }

    private LinearLayout weeklyTrendRow(int weekNumber, long weekStart, long weekEnd, long totalMs, long launches, long max) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(weekStart);
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(Math.max(weekStart, weekEnd - 1));
        String label = "Week " + weekNumber + " (" +
                start.get(Calendar.DAY_OF_MONTH) + "-" + end.get(Calendar.DAY_OF_MONTH) + ")";

        LinearLayout labelRow = ui.horizontalRow();
        TextView week = ui.text(label, 13, Color.rgb(52, 64, 84), true);
        TextView value = ui.text(formatDuration(totalMs) + " | " + launches + (launches == 1 ? " open" : " opens"),
                13, Color.rgb(0, 110, 130), true);
        value.setGravity(Gravity.RIGHT);
        labelRow.addView(week, ui.weightParams());
        labelRow.addView(value, ui.weightParams());
        row.addView(labelRow);
        row.addView(ui.progressBar(totalMs, max, Color.rgb(0, 110, 130)));
        return row;
    }

    private void clearSelectedApp() {
        selectedPackageName = null;
        selectedAppName = null;
        selectedCategory = null;
    }

    private String loadAppLabel(String packageName, String fallbackName) {
        PackageManager packageManager = getPackageManager();
        try {
            CharSequence label = packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0));
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return cleanFallbackName(packageName, fallbackName);
    }

    private Drawable loadAppIcon(String packageName) {
        PackageManager packageManager = getPackageManager();
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException ex) {
            return getApplicationInfo().loadIcon(packageManager);
        }
    }

    private String cleanFallbackName(String packageName, String fallbackName) {
        if (fallbackName != null && !fallbackName.trim().isEmpty() && !fallbackName.equals(packageName)) {
            return fallbackName;
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            return "Unknown app";
        }
        String[] parts = packageName.split("\\.");
        String candidate = parts.length == 0 ? packageName : parts[parts.length - 1];
        candidate = candidate.replace('_', ' ').replace('-', ' ').trim();
        if (candidate.isEmpty()) {
            return packageName;
        }
        return candidate.substring(0, 1).toUpperCase(Locale.US) + candidate.substring(1);
    }

    private void renderFrequentOpenWarnings(UsageRange range) {
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("App Open Frequency"));
        Cursor cursor = db.getFrequentOpenApps(range.start, range.end, 5);
        boolean any = false;
        try {
            while (cursor.moveToNext()) {
                String packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name"));
                if (hideSystemApps && isSystemApp(packageName)) {
                    continue;
                }
                any = true;
                String appName = loadAppLabel(packageName, cursor.getString(cursor.getColumnIndexOrThrow("app_name")));
                long launches = cursor.getLong(cursor.getColumnIndexOrThrow("launches"));
                long totalMs = cursor.getLong(cursor.getColumnIndexOrThrow("total_ms"));
                // Only highlight red for suspiciously high open counts
                int nameColor = launches >= 15 ? COLOR_RED : COLOR_NAVY_TEXT;
                panel.addView(ui.text(appName + " \u2022 opened " + launches + " times",
                        14, nameColor, launches >= 15));
                panel.addView(ui.text("Total screen time: " + formatDuration(totalMs),
                        13, COLOR_MUTED, false));
                ui.addSpace(panel, 8);
            }
        } finally {
            cursor.close();
        }
        if (!any) {
            panel.addView(ui.text("No high-frequency app opens in this range.",
                    14, Color.rgb(102, 112, 133), false));
        }
        content.addView(panel);
    }

    private void renderBestWorstDays(UsageRange range) {
        if (TAB_TODAY.equals(selectedTab)) {
            return;
        }
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Best / Busiest Day"));
        long bestUsage = Long.MAX_VALUE;
        long busiestUsage = 0;
        long bestDay = 0;
        long busiestDay = 0;
        for (int index = 0; index < range.dayCount(); index++) {
            long dayStart = range.start + index * UsageRange.DAY_MS;
            UsageRange dayRange = new UsageRange(dayStart, dayStart + UsageRange.DAY_MS);
            long total = db.getSummary(dayRange.start, dayRange.end).get("total_ms");
            if (total < bestUsage) {
                bestUsage = total;
                bestDay = dayStart;
            }
            if (total > busiestUsage) {
                busiestUsage = total;
                busiestDay = dayStart;
            }
        }
        panel.addView(ui.compactMetricRow("Lowest usage", dayFormat.format(new Date(bestDay)) + " | " + formatDuration(bestUsage), Color.rgb(2, 122, 72)));
        panel.addView(ui.compactMetricRow("Highest usage", dayFormat.format(new Date(busiestDay)) + " | " + formatDuration(busiestUsage), Color.rgb(180, 35, 24)));
        long currentTotal = db.getSummary(range.start, range.end).get("total_ms");
        UsageRange previous = previousRange(range);
        long previousTotal = db.getSummary(previous.start, previous.end).get("total_ms");
        panel.addView(ui.text("Previous period: " + comparisonText(currentTotal, previousTotal),
                13, Color.rgb(71, 84, 103), false));
        content.addView(panel);
    }

    private void renderHourlyBreakdown(long dateStart) {
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Hourly Breakdown"));
        long[] hours = new long[24];
        long max = 0;
        Cursor cursor = db.getHourlyUsage(dateStart);
        try {
            while (cursor.moveToNext()) {
                int hour = cursor.getInt(cursor.getColumnIndexOrThrow("hour"));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow("total_ms"));
                if (hour >= 0 && hour < hours.length) {
                    hours[hour] = total;
                    max = Math.max(max, total);
                }
            }
        } finally {
            cursor.close();
        }

        boolean any = false;
        for (int hour = 0; hour < 24; hour++) {
            if (hours[hour] <= 0) {
                continue;
            }
            any = true;
            panel.addView(ui.compactMetricRow(String.format(Locale.US, "%02d:00", hour),
                    formatDuration(hours[hour]), Color.rgb(22, 63, 95)));
            panel.addView(ui.progressBar(hours[hour], max, Color.rgb(0, 110, 130)));
            ui.addSpace(panel, 8);
        }
        if (!any) {
            panel.addView(ui.text("No hourly usage captured today yet.", 14, Color.rgb(102, 112, 133), false));
        }
        content.addView(panel);
    }

    private void renderCategoryBreakdown(UsageRange range) {
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Categories"));
        Cursor cursor = db.getCategoryUsage(range.start, range.end);
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(ui.text("No category data yet.", 14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    String category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
                    long total = cursor.getLong(cursor.getColumnIndexOrThrow("total_ms"));
                    long launches = cursor.getLong(cursor.getColumnIndexOrThrow("launches"));
                    long rangeTotal = db.getSummary(range.start, range.end).get("total_ms");
                    panel.addView(ui.compactMetricRow(category, formatDuration(total), Color.rgb(22, 63, 95)));
                    panel.addView(ui.text(launches + " opens | " + percentText(total, rangeTotal),
                            13, Color.rgb(71, 84, 103), false));
                    ui.addSpace(panel, 8);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        content.addView(panel);
    }

    private UsageRange selectedRange() {
        if (TAB_WEEK.equals(selectedTab)) {
            return UsageRange.thisWeek();
        }
        if (TAB_MONTH.equals(selectedTab)) {
            return UsageRange.thisMonth();
        }
        return UsageRange.today();
    }

    private void startSync(final int days, final boolean showToast) {
        if (!UsageTracker.hasUsageAccess(this) || syncInProgress) {
            return;
        }
        syncInProgress = true;
        render();
        final android.content.Context appContext = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] error = new String[1];
                try {
                    UsageTracker.syncRecentDays(appContext, days);
                } catch (Throwable throwable) {
                    error[0] = throwable.getMessage();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        syncInProgress = false;
                        if (error[0] != null) {
                            Toast.makeText(UsageTrackerActivity.this, "Sync failed: " + error[0], Toast.LENGTH_SHORT).show();
                        } else if (showToast) {
                            Toast.makeText(UsageTrackerActivity.this, "Usage synced", Toast.LENGTH_SHORT).show();
                        }
                        render();
                    }
                });
            }
        }).start();
    }

    private String appDetails(String category, long launches, long firstOpen, long lastOpen, boolean installed) {
        String details = category + " | " + launches + (launches == 1 ? " open" : " opens");
        if (!installed) {
            details += " | Uninstalled";
        }
        if (firstOpen > 0 && lastOpen > 0) {
            details += " | " + timeFormat.format(new Date(lastOpen));
        }
        return details;
    }

    private long appTotalForRange(String packageName, UsageRange range) {
        Cursor cursor = db.getAppUsageSummary(packageName, range.start, range.end);
        try {
            return cursor.moveToFirst() ? cursor.getLong(cursor.getColumnIndexOrThrow("total_ms")) : 0;
        } finally {
            cursor.close();
        }
    }

    private String percentText(long amount, long total) {
        if (total <= 0) {
            return "0%";
        }
        return String.format(Locale.US, "%.0f%%", (amount * 100.0) / total);
    }

    private UsageRange previousRange(UsageRange range) {
        long length = range.end - range.start;
        return new UsageRange(range.start - length, range.start);
    }

    private String comparisonText(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? "new usage this period" : "no previous data";
        }
        long diff = current - previous;
        String direction = diff >= 0 ? "up " : "down ";
        return direction + formatDuration(Math.abs(diff)) + " vs previous period";
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    private boolean isSystemApp(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    private SharedPreferences preferences() {
        return getSharedPreferences("digital_tracker", MODE_PRIVATE);
    }

    private String formatDuration(long millis) {
        if (millis > 0 && millis < 60000L) {
            return "<1m";
        }
        long minutes = Math.max(0, millis / 60000L);
        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        if (hours <= 0) {
            return remainingMinutes + "m";
        }
        return hours + "h " + remainingMinutes + "m";
    }
}
