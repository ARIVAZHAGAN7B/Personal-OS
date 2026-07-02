package com.ariva.personalos;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class UsageTrackerActivity extends Activity {
    private static final String TAB_TODAY = "Today";
    private static final String TAB_WEEK = "Week";
    private static final String TAB_MONTH = "Month";

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
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new UsageDbHelper(this);
        ui = new AppUi(this);
        hideSystemApps = preferences().getBoolean("hide_system_apps", true);
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
        if (selectedPackageName != null) {
            clearSelectedApp();
            render();
            return;
        }
        super.onBackPressed();
    }

    private void render() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 249, 252));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(mainPageBack());
        ui.addSpace(content, 6);
        content.addView(ui.text("Digital Usage", 28, Color.rgb(16, 24, 40), true));
        content.addView(ui.text("Daily, weekly, and monthly app usage saved inside Personal OS.",
                14, Color.rgb(71, 84, 103), false));
        ui.addSpace(content, 12);

        if (!UsageTracker.hasUsageAccess(this)) {
            renderPermissionPanel();
            setContentView(scrollView);
            return;
        }

        renderActions();
        ui.addSpace(content, 12);
        renderTabs();
        ui.addSpace(content, 14);

        UsageRange range = selectedRange();
        if (selectedPackageName != null) {
            renderAppDetail(range);
            setContentView(scrollView);
            return;
        }

        renderSummary(range);
        ui.addSpace(content, 14);
        renderTopApps(range);
        ui.addSpace(content, 14);
        renderFrequentOpenWarnings(range);
        ui.addSpace(content, 14);
        if (TAB_TODAY.equals(selectedTab)) {
            renderHourlyBreakdown(range.start);
            ui.addSpace(content, 14);
        }
        renderCategoryBreakdown(range);
        ui.addSpace(content, 14);
        renderBestWorstDays(range);
        setContentView(scrollView);
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
        LinearLayout row = ui.horizontalRow();
        row.addView(ui.actionButton("Sync Now", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSync(60, true);
            }
        }), ui.weightParams());
        row.addView(ui.actionButton("Usage Settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            }
        }), ui.weightParams());
        content.addView(row);

        ui.addSpace(content, 8);
        Button toggleSystem = ui.actionButton(hideSystemApps ? "Show System Apps" : "Hide System Apps", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSystemApps = !hideSystemApps;
                preferences().edit().putBoolean("hide_system_apps", hideSystemApps).apply();
                render();
            }
        });
        content.addView(toggleSystem, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        long lastSync = db.getLastSyncTime();
        if (lastSync > 0) {
            ui.addSpace(content, 8);
            content.addView(ui.text("Last sync: " + dateTimeFormat.format(new Date(lastSync)),
                    13, Color.rgb(71, 84, 103), false));
        } else if (syncInProgress) {
            ui.addSpace(content, 8);
            content.addView(ui.text("Syncing usage in background...",
                    13, Color.rgb(71, 84, 103), false));
        }
    }

    private void renderTabs() {
        LinearLayout row = ui.horizontalRow();
        row.addView(tabButton(TAB_TODAY), ui.weightParams());
        row.addView(tabButton(TAB_WEEK), ui.weightParams());
        row.addView(tabButton(TAB_MONTH), ui.weightParams());
        content.addView(row);
    }

    private Button tabButton(final String tab) {
        boolean selected = tab.equals(selectedTab);
        Button button = new Button(this);
        button.setText(tab);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(selected ? Color.WHITE : Color.rgb(16, 24, 40));
        button.setBackgroundColor(selected ? Color.rgb(22, 63, 95) : Color.WHITE);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTab = tab;
                clearSelectedApp();
                render();
            }
        });
        return button;
    }

    private void renderSummary(UsageRange range) {
        Map<String, Long> summary = db.getSummary(range.start, range.end);
        LinearLayout row = ui.horizontalRow();
        row.addView(ui.metricCard("Screen time", formatDuration(summary.get("total_ms"))), ui.weightParams());
        row.addView(ui.metricCard("Opens", String.valueOf(summary.get("launches"))), ui.weightParams());
        content.addView(row);
        ui.addSpace(content, 8);
        row = ui.horizontalRow();
        row.addView(ui.metricCard("Apps used", String.valueOf(summary.get("app_count"))), ui.weightParams());
        row.addView(ui.metricCard("Daily avg", formatDuration(summary.get("total_ms") / range.dayCount())), ui.weightParams());
        content.addView(row);
    }

    private void renderTopApps(UsageRange range) {
        content.addView(ui.sectionTitle("Most Used Apps"));
        LinearLayout panel = ui.panel();
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
                selectedPackageName = packageName;
                selectedAppName = displayName;
                selectedCategory = installed ? category : category + " | Uninstalled";
                render();
            }
        });

        return row;
    }

    private void renderAppDetail(UsageRange range) {
        content.addView(backLink());
        ui.addSpace(content, 6);

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
        titles.addView(ui.text(selectedAppName, 22, Color.rgb(16, 24, 40), true));
        titles.addView(ui.text(selectedCategory == null ? "App usage details" : selectedCategory,
                13, Color.rgb(71, 84, 103), false));
        row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(row);
        content.addView(header);

        ui.addSpace(content, 14);
        renderAppDetailSummary(range);
        ui.addSpace(content, 14);
        renderUsageLimitControls();
        ui.addSpace(content, 14);
        renderAppTrend(range);
    }

    private TextView backLink() {
        TextView back = ui.text("< Back to apps", 15, Color.rgb(0, 110, 130), true);
        back.setPadding(0, 0, 0, ui.dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearSelectedApp();
                render();
            }
        });
        return back;
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

        LinearLayout row = ui.horizontalRow();
        row.addView(ui.metricCard("Used", formatDuration(totalMs)), ui.weightParams());
        row.addView(ui.metricCard("Opened", String.valueOf(launches)), ui.weightParams());
        content.addView(row);
        ui.addSpace(content, 8);

        row = ui.horizontalRow();
        row.addView(ui.metricCard("Daily avg", formatDuration(totalMs / Math.max(1, range.dayCount()))), ui.weightParams());
        row.addView(ui.metricCard("Active days", activeDays + "/" + range.dayCount()), ui.weightParams());
        content.addView(row);

        ui.addSpace(content, 10);
        LinearLayout panel = ui.panel();
        panel.addView(ui.text("First open: " + (firstOpen > 0 ? dateTimeFormat.format(new Date(firstOpen)) : "No data"),
                13, Color.rgb(71, 84, 103), false));
        panel.addView(ui.text("Last open: " + (lastOpen > 0 ? dateTimeFormat.format(new Date(lastOpen)) : "No data"),
                13, Color.rgb(71, 84, 103), false));
        content.addView(panel);
    }

    private void renderUsageLimitControls() {
        long dailyLimit = db.getDailyLimit(selectedPackageName);
        long usedToday = appTotalForRange(selectedPackageName, UsageRange.today());
        LinearLayout panel = ui.panel();
        panel.addView(ui.text("Daily limit", 17, Color.rgb(16, 24, 40), true));
        if (dailyLimit <= 0) {
            panel.addView(ui.text("No daily limit set.", 13, Color.rgb(71, 84, 103), false));
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
        panel.addView(limitButton("Clear Limit", 0));
        content.addView(panel);
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
        content.addView(ui.sectionTitle(TAB_WEEK.equals(selectedTab) ? "Weekly Trend" : "Daily Trend"));
        LinearLayout panel = ui.panel();
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
        content.addView(ui.sectionTitle("Weekly Usage"));
        LinearLayout panel = ui.panel();
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
        content.addView(ui.sectionTitle("Open Frequency"));
        LinearLayout panel = ui.panel();
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
                panel.addView(ui.text(appName + " was opened " + launches + " times",
                        14, Color.rgb(180, 35, 24), true));
                panel.addView(ui.text("Total time: " + formatDuration(totalMs),
                        13, Color.rgb(71, 84, 103), false));
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
        content.addView(ui.sectionTitle("Best / Busiest Day"));
        LinearLayout panel = ui.panel();
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
        content.addView(ui.sectionTitle("Hourly Breakdown"));
        LinearLayout panel = ui.panel();
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
        content.addView(ui.sectionTitle("Categories"));
        LinearLayout panel = ui.panel();
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
