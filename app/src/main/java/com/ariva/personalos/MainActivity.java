package com.ariva.personalos;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    public static final String ACTION_ADD_EXPENSE = "com.ariva.personalos.ADD_EXPENSE";

    private static final int PAGE_DASHBOARD = 0;
    private static final int PAGE_TRANSACTIONS = 1;
    private static final int PAGE_INSIGHTS = 2;
    private static final int PAGE_MORE = 3;
    private static final int MORE_HOME = 0;
    private static final int MORE_ACCOUNTS = 1;
    private static final int MORE_CATEGORIES = 2;
    private static final int MORE_BORROW_LEND = 3;
    private static final String INSIGHTS_TODAY = "Today";
    private static final String INSIGHTS_WEEKLY = "Weekly";
    private static final String INSIGHTS_MONTHLY = "Monthly";
    private static final int COLOR_NAVY = Color.rgb(22, 63, 95);
    private static final int COLOR_NAVY_TEXT = Color.rgb(13, 34, 54);
    private static final int COLOR_METRIC_TINT = Color.rgb(234, 241, 246);
    private static final int COLOR_TEAL = Color.rgb(0, 110, 130);
    private static final int COLOR_TEAL_DARK = Color.rgb(13, 132, 143);
    private static final int COLOR_RED = Color.rgb(180, 35, 24);
    private static final int COLOR_GREEN = Color.rgb(15, 157, 88);
    private static final int COLOR_MUTED = Color.rgb(91, 107, 123);
    private static final int COLOR_SCREEN = Color.rgb(247, 249, 252);
    private static final int COLOR_BORDER = Color.rgb(224, 228, 236);
    private static final int COLOR_FIELD = Color.rgb(250, 252, 254);
    private static final int[] CHART_COLORS = new int[]{
            COLOR_TEAL,
            COLOR_NAVY,
            COLOR_GREEN,
            Color.rgb(245, 158, 11),
            COLOR_RED,
            Color.rgb(99, 102, 241)
    };

    private ExpenseDbHelper db;
    private LinearLayout content;
    private LinearLayout transactionResultsContainer;
    private boolean showingExpenseTracker = false;
    private boolean showingAppSettings = false;
    private TextView updateStatusView;
    private int currentPage = PAGE_DASHBOARD;
    private int currentMorePage = MORE_HOME;
    private String transactionFilterType = "all";
    private long transactionFilterAccountId = -1;
    private long transactionFilterCategoryId = -1;
    private String transactionFilterTimeline = "all";
    private String transactionSortOrder = "newest";
    private String transactionSearchText = "";
    private String insightsTab = INSIGHTS_TODAY;
    private boolean dashboardWeeklyGraphShowsExpense = true;
    private final SimpleDateFormat shortDateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new ExpenseDbHelper(this);
        requestNotificationPermission();
        scheduleDailySummary();
        if (ACTION_ADD_EXPENSE.equals(getIntent().getAction())) {
            currentPage = PAGE_TRANSACTIONS;
            currentMorePage = MORE_HOME;
            renderAppShell();
            showTransactionDialog(ExpenseDbHelper.TYPE_EXPENSE);
            return;
        }
        renderHome();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_ADD_EXPENSE.equals(intent.getAction())) {
            currentPage = PAGE_TRANSACTIONS;
            currentMorePage = MORE_HOME;
            renderAppShell();
            showTransactionDialog(ExpenseDbHelper.TYPE_EXPENSE);
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (showingAppSettings) {
            renderHome();
            return;
        }
        if (showingExpenseTracker) {
            if (currentPage == PAGE_MORE && currentMorePage != MORE_HOME) {
                currentMorePage = MORE_HOME;
                renderAppShell();
                return;
            }
            if (currentPage != PAGE_DASHBOARD) {
                selectPage(PAGE_DASHBOARD);
                return;
            }
            super.onBackPressed();
            return;
        }
        super.onBackPressed();
    }

    private void renderHome() {
        showingExpenseTracker = false;
        showingAppSettings = false;
        updateStatusView = null;
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_SCREEN);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(18), dp(14), dp(18));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout firstRow = horizontalRow();
        firstRow.addView(imageToolTile(R.drawable.expense_tracker_logo, "Expense tracker", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPage(PAGE_DASHBOARD);
            }
        }), toolGridParams());
        firstRow.addView(imageToolTile(R.drawable.digi_tracker_logo, "Digital usage tracker", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, UsageTrackerActivity.class));
            }
        }), toolGridParams());
        content.addView(firstRow);

        addSpace(10);
        LinearLayout secondRow = horizontalRow();
        secondRow.addView(imageToolTile(R.drawable.ic_diary, "Diary", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DiaryActivity.class));
            }
        }), toolGridParams());
        secondRow.addView(imageToolTile(R.drawable.ic_nav_settings, "App settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderPersonalOsSettings();
            }
        }), toolGridParams());
        content.addView(secondRow);

        setContentView(scrollView);
        maybeCheckForUpdates();
    }

    private void renderPersonalOsSettings() {
        showingExpenseTracker = false;
        showingAppSettings = true;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_SCREEN);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(18), dp(14), dp(18));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(mainPageBack());
        content.addView(pageTitle("App settings"));
        content.addView(pageSubtitle("Version and update preferences."));
        addSpace(16);

        SharedPreferences preferences = getSharedPreferences(UpdateChecker.PREFS, MODE_PRIVATE);
        LinearLayout updatePanel = panel();
        addPanelTitle(updatePanel, "Application update");
        updatePanel.addView(compactMetricRow(
                "Installed version",
                UpdateChecker.installedVersionName(this) + " (" + UpdateChecker.installedVersionCode(this) + ")",
                COLOR_NAVY_TEXT));
        addSpaceTo(updatePanel, 10);

        Switch automaticChecks = new Switch(this);
        automaticChecks.setText("Automatic update checks");
        automaticChecks.setTextColor(COLOR_NAVY_TEXT);
        automaticChecks.setTextSize(14);
        automaticChecks.setChecked(preferences.getBoolean(UpdateChecker.PREF_AUTO_CHECK, true));
        automaticChecks.setPadding(0, dp(4), 0, dp(8));
        automaticChecks.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(UpdateChecker.PREF_AUTO_CHECK, isChecked).apply());
        updatePanel.addView(automaticChecks);

        updateStatusView = text("Ready", 12, COLOR_MUTED, false);
        updateStatusView.setPadding(0, dp(2), 0, dp(10));
        updatePanel.addView(updateStatusView);
        updatePanel.addView(actionButton("Check for updates", v -> checkForUpdates(true)));
        content.addView(updatePanel);

        addSpace(16);
        LinearLayout sourcePanel = panel();
        addPanelTitle(sourcePanel, "Release feed");
        final EditText feedUrl = editText("HTTPS update feed URL");
        feedUrl.setSingleLine(true);
        feedUrl.setText(UpdateChecker.feedUrl(this));
        addDialogField(sourcePanel, "Feed URL", feedUrl);
        sourcePanel.addView(actionButton("Save update source", v -> {
            String value = feedUrl.getText().toString().trim();
            Uri uri = Uri.parse(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || TextUtils.isEmpty(uri.getHost())) {
                toast("Enter a valid HTTPS URL.");
                return;
            }
            preferences.edit()
                    .putString(UpdateChecker.PREF_FEED_URL, value)
                    .putLong(UpdateChecker.PREF_LAST_CHECK, 0)
                    .apply();
            updateStatusView.setText("Update source saved");
        }));
        content.addView(sourcePanel);

        setContentView(scrollView);
    }

    private void maybeCheckForUpdates() {
        SharedPreferences preferences = getSharedPreferences(UpdateChecker.PREFS, MODE_PRIVATE);
        if (!preferences.getBoolean(UpdateChecker.PREF_AUTO_CHECK, true)) {
            return;
        }
        long lastCheck = preferences.getLong(UpdateChecker.PREF_LAST_CHECK, 0);
        if (System.currentTimeMillis() - lastCheck < UpdateChecker.AUTO_CHECK_INTERVAL_MS) {
            return;
        }
        checkForUpdates(false);
    }

    private void checkForUpdates(boolean userInitiated) {
        if (userInitiated && updateStatusView != null) {
            updateStatusView.setText("Checking...");
        }
        UpdateChecker.check(this, (update, error) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (error != null) {
                if (userInitiated) {
                    String message = error.getMessage() == null ? "Update check failed." : error.getMessage();
                    if (updateStatusView != null) {
                        updateStatusView.setText(message);
                    } else {
                        toast(message);
                    }
                }
                return;
            }

            getSharedPreferences(UpdateChecker.PREFS, MODE_PRIVATE)
                    .edit()
                    .putLong(UpdateChecker.PREF_LAST_CHECK, System.currentTimeMillis())
                    .apply();
            long installedCode = UpdateChecker.installedVersionCode(this);
            if (update.versionCode > installedCode) {
                if (updateStatusView != null) {
                    updateStatusView.setText("Version " + update.versionName + " is available");
                }
                showUpdateAvailableDialog(update);
            } else if (userInitiated && updateStatusView != null) {
                updateStatusView.setText("PersonalOS is up to date");
            }
        });
    }

    private void showUpdateAvailableDialog(UpdateChecker.UpdateInfo update) {
        String notes = TextUtils.isEmpty(update.releaseNotes)
                ? "A new PersonalOS version is available."
                : update.releaseNotes;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("Version " + update.versionName + "\n\n" + notes)
                .setPositiveButton("Download", (ignored, which) -> openUpdateDownload(update.downloadUrl()))
                .setNegativeButton("Later", null)
                .create();
        showDialog(dialog);
    }

    private void openUpdateDownload(String url) {
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || TextUtils.isEmpty(uri.getHost())) {
            toast("The update download URL is invalid.");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception error) {
            toast("No browser is available to download the update.");
        }
    }

    private void renderAppShell() {
        showingExpenseTracker = true;
        showingAppSettings = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_SCREEN);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_SCREEN);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(18));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(mainPageBack());
        addSpace(8);
        renderCurrentPage();

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(renderBottomNav(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void renderCurrentPage() {
        if (currentPage == PAGE_TRANSACTIONS) {
            renderTransactionsPage();
        } else if (currentPage == PAGE_INSIGHTS) {
            renderInsightsPage();
        } else if (currentPage == PAGE_MORE) {
            renderMorePage();
        } else {
            renderDashboardPage();
        }
    }

    private void selectPage(int page) {
        currentPage = page;
        if (page != PAGE_MORE) {
            currentMorePage = MORE_HOME;
        }
        renderAppShell();
    }

    private LinearLayout renderBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(6), dp(6), dp(7));
        nav.setBackground(tileBackground(Color.WHITE, COLOR_BORDER));
        nav.addView(createBottomNavItem(R.drawable.ic_nav_home, "Dashboard", PAGE_DASHBOARD), weightParams());
        nav.addView(createBottomNavItem(R.drawable.ic_nav_transactions, "Accounts", PAGE_TRANSACTIONS), weightParams());
        nav.addView(createCenterAddButton(), weightParams());
        nav.addView(createBottomNavItem(R.drawable.ic_nav_analytics, "Analytics", PAGE_INSIGHTS), weightParams());
        nav.addView(createBottomNavItem(R.drawable.ic_nav_settings, "Settings", PAGE_MORE), weightParams());
        return nav;
    }

    private View createBottomNavItem(int iconRes, String label, final int page) {
        boolean selected = currentPage == page;
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setMinimumHeight(dp(46));
        item.setPadding(dp(2), dp(1), dp(2), dp(1));
        item.setBackground(tileBackground(Color.WHITE, Color.TRANSPARENT));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(selected ? COLOR_GREEN : COLOR_MUTED);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        item.addView(icon, iconParams);

        TextView text = text(label, 9, selected ? COLOR_GREEN : COLOR_MUTED, selected);
        styleBottomNavLabel(text);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        item.addView(text);

        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPage(page);
            }
        });
        return item;
    }

    private View createCenterAddButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(54));
        button.setPadding(dp(2), 0, dp(2), 0);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_nav_add);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(COLOR_GREEN);
        icon.setBackground(background);
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView label = text("Add", 9, COLOR_MUTED, false);
        styleBottomNavLabel(label);
        label.setGravity(Gravity.CENTER);
        button.addView(label);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            icon.setElevation(dp(5));
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddTransactionDialog();
            }
        });
        return button;
    }

    private ImageView filterIconButton(View.OnClickListener listener) {
        ImageView button = new ImageView(this);
        button.setImageResource(R.drawable.ic_filter_list);
        button.setColorFilter(COLOR_NAVY);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setBackground(tileBackground(COLOR_METRIC_TINT, COLOR_BORDER));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setContentDescription("Filter transactions");
        button.setOnClickListener(listener);
        return button;
    }

    private void renderDashboardPage() {
        content.addView(appTitle("Expense tracker"));
        content.addView(pageSubtitle(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date())));
        addSpace(16);
        renderSummary();
        addSpace(12);
        renderWeeklyDashboardGraph();
    }

    private void renderTransactionsPage() {
        content.addView(pageTitle("Transactions"));
        content.addView(pageSubtitle("Filter, review, and export records."));
        addSpace(16);
        renderTransactionFilters();
        addSpace(12);
        Button export = actionButton("Export CSV", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportTransactionsCsv();
            }
        });
        content.addView(export, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addSpace(18);
        transactionResultsContainer = new LinearLayout(this);
        transactionResultsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(transactionResultsContainer);
        renderFilteredTransactions();
    }

    private void renderInsightsPage() {
        content.addView(pageTitle("Insights"));
        addSpace(10);
        renderInsightsTabSwitcher(content);
        addSpace(10);
        if (INSIGHTS_WEEKLY.equals(insightsTab)) {
            renderWeeklyInsights(content);
        } else if (INSIGHTS_MONTHLY.equals(insightsTab)) {
            renderMonthlyInsights(content);
        } else {
            renderTodayInsights(content);
        }
    }

    private void renderInsightsTabSwitcher(LinearLayout parent) {
        LinearLayout row = horizontalRow();
        row.addView(insightsTabButton(INSIGHTS_TODAY), weightParams());
        row.addView(insightsTabButton(INSIGHTS_WEEKLY), weightParams());
        row.addView(insightsTabButton(INSIGHTS_MONTHLY), weightParams());
        parent.addView(row);
    }

    private Button insightsTabButton(final String tab) {
        boolean selected = tab.equals(insightsTab);
        Button button = new Button(this);
        button.setText(tab);
        styleButton(button);
        button.setTextColor(selected ? Color.WHITE : COLOR_NAVY_TEXT);
        button.setAllCaps(false);
        button.setBackground(tileBackground(selected ? COLOR_TEAL : Color.WHITE, selected ? Color.TRANSPARENT : COLOR_BORDER));
        button.setPadding(dp(4), dp(8), dp(4), dp(8));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insightsTab = tab;
                renderAppShell();
            }
        });
        return button;
    }

    private void renderTodayInsights(LinearLayout parent) {
        long start = startOfDay();
        long end = nextDayStart();
        Map<String, Long> summary = db.getIncomeExpenseSummary(start, end);
        long income = summary.get("income");
        long expense = summary.get("expense");
        long net = income - expense;

        addAnalyticsSummaryPanel(parent, "Today Summary",
                new String[]{"Total Spend Today", "Total Income Today", "Net Today"},
                new String[]{ExpenseDbHelper.formatMoney(expense), ExpenseDbHelper.formatMoney(income), signedMoney(net)});

        addSpace(16);
        LinearLayout transactionsPanel = panel();
        addPanelTitle(transactionsPanel, "Today Transactions");
        Cursor transactions = db.getTransactionsBetween(start, end);
        try {
            if (!transactions.moveToFirst()) {
                transactionsPanel.addView(text("No transactions today yet.", 14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    final long id = transactions.getLong(transactions.getColumnIndexOrThrow("id"));
                    String type = transactions.getString(transactions.getColumnIndexOrThrow("type"));
                    String name = transactions.getString(transactions.getColumnIndexOrThrow("name"));
                    String category = transactions.getString(transactions.getColumnIndexOrThrow("category"));
                    String account = transactions.getString(transactions.getColumnIndexOrThrow("account"));
                    long amount = transactions.getLong(transactions.getColumnIndexOrThrow("amount"));
                    long date = transactions.getLong(transactions.getColumnIndexOrThrow("transaction_date"));
                    transactionsPanel.addView(transactionRow(id, name, type, amount, date, category, account));
                    addSpaceTo(transactionsPanel, 8);
                } while (transactions.moveToNext());
            }
        } finally {
            transactions.close();
        }
        parent.addView(transactionsPanel);

        addSpace(16);
        LinearLayout breakdownPanel = panel();
        addPanelTitle(breakdownPanel, "Today Category Breakdown");
        addInsightCategoryRows(breakdownPanel, "Expenses", ExpenseDbHelper.TYPE_EXPENSE, start, end, expense, false);
        addSpaceTo(breakdownPanel, 12);
        addInsightCategoryRows(breakdownPanel, "Income", ExpenseDbHelper.TYPE_INCOME, start, end, income, false);
        parent.addView(breakdownPanel);

    }

    private void renderWeeklyInsights(LinearLayout parent) {
        long start = startOfWeek();
        long end = nextWeekStart();
        Map<String, Long> summary = db.getIncomeExpenseSummary(start, end);
        long income = summary.get("income");
        long expense = summary.get("expense");
        long net = income - expense;
        long balance = db.getTotalBalance();

        addAnalyticsSummaryPanel(parent, "Weekly Summary",
                new String[]{"Income This Week", "Spend This Week", "Net Result", "Current Balance"},
                new String[]{ExpenseDbHelper.formatMoney(income), ExpenseDbHelper.formatMoney(expense), signedMoney(net),
                        ExpenseDbHelper.formatMoney(balance)});

        if (summary.get("transaction_count") == 0) {
            addSpace(10);
            parent.addView(emptyPanel("No weekly data available."));
        }

        addSpace(16);
        renderCashflowChart(parent, "Weekly Cashflow", start, end, 7);

        addSpace(16);
        LinearLayout expensePanel = panel();
        addPanelTitle(expensePanel, "Weekly Expense Categories");
        addCategoryPieChart(expensePanel, ExpenseDbHelper.TYPE_EXPENSE, start, end, expense);
        addInsightCategoryRows(expensePanel, "", ExpenseDbHelper.TYPE_EXPENSE, start, end, expense, true);
        parent.addView(expensePanel);

        addSpace(16);
        LinearLayout incomePanel = panel();
        addPanelTitle(incomePanel, "Weekly Income Sources");
        addCategoryPieChart(incomePanel, ExpenseDbHelper.TYPE_INCOME, start, end, income);
        addInsightCategoryRows(incomePanel, "", ExpenseDbHelper.TYPE_INCOME, start, end, income, true);
        parent.addView(incomePanel);

        addSpace(16);
        LinearLayout accountPanel = panel();
        addPanelTitle(accountPanel, "Weekly Account Activity");
        addAccountActivityRows(accountPanel, start, end, true);
        parent.addView(accountPanel);

        addSpace(16);
        renderBorrowLendAnalytics(parent, "Weekly Borrow / Lend Movement", start, end, false);

    }

    private void renderMonthlyInsights(LinearLayout parent) {
        long currentStart = startOfMonth();
        long currentEnd = nextMonthStart();
        Map<String, Long> summary = db.getIncomeExpenseSummary(currentStart, currentEnd);
        long income = summary.get("income");
        long expense = summary.get("expense");
        long net = income - expense;
        long balance = db.getTotalBalance();

        addAnalyticsSummaryPanel(parent, "Monthly Summary",
                new String[]{"Monthly Income", "Monthly Spend", "Net Result", "Current Balance"},
                new String[]{ExpenseDbHelper.formatMoney(income), ExpenseDbHelper.formatMoney(expense), signedMoney(net),
                        ExpenseDbHelper.formatMoney(balance)});

        if (summary.get("transaction_count") == 0) {
            addSpace(10);
            parent.addView(emptyPanel("No monthly data available."));
        }

        addSpace(16);
        renderDailySpendingTrend(parent, currentStart, currentEnd);

        addSpace(16);
        renderCashflowChart(parent, "Monthly Cashflow", currentStart, currentEnd, daysInCurrentMonth());

        addSpace(16);
        LinearLayout categoryPanel = panel();
        addPanelTitle(categoryPanel, "Monthly Expense Categories");
        addCategoryPieChart(categoryPanel, ExpenseDbHelper.TYPE_EXPENSE, currentStart, currentEnd, expense);
        addMonthlyCategoryAnalytics(categoryPanel, ExpenseDbHelper.TYPE_EXPENSE, currentStart, currentEnd, expense);
        parent.addView(categoryPanel);

        addSpace(16);
        LinearLayout sourcePanel = panel();
        addPanelTitle(sourcePanel, "Monthly Income Sources");
        addCategoryPieChart(sourcePanel, ExpenseDbHelper.TYPE_INCOME, currentStart, currentEnd, income);
        addMonthlyCategoryAnalytics(sourcePanel, ExpenseDbHelper.TYPE_INCOME, currentStart, currentEnd, income);
        parent.addView(sourcePanel);

        addSpace(16);
        LinearLayout topPanel = panel();
        addPanelTitle(topPanel, "Top Spending Categories");
        addTopSpendingRows(topPanel, currentStart, currentEnd, expense);
        parent.addView(topPanel);

        addSpace(16);
        LinearLayout accountPanel = panel();
        addPanelTitle(accountPanel, "Monthly Account Analytics");
        addAccountActivityRows(accountPanel, currentStart, currentEnd, false);
        parent.addView(accountPanel);

        addSpace(16);
        renderAccountTrendOverview(parent, currentStart, currentEnd);

        addSpace(16);
        renderBorrowLendAnalytics(parent, "Borrow / Lend Summary", currentStart, currentEnd, true);

    }

    private void renderMorePage() {
        if (currentMorePage == MORE_ACCOUNTS) {
            renderAccountsPage();
            return;
        }
        if (currentMorePage == MORE_CATEGORIES) {
            renderCategoriesPage();
            return;
        }
        if (currentMorePage == MORE_BORROW_LEND) {
            renderBorrowLendPage();
            return;
        }

        content.addView(pageTitle("More"));
        content.addView(pageSubtitle("Accounts, categories, and setup."));
        addSpace(16);
        content.addView(moreMenuCard("Accounts", "Cash, bank accounts, wallets", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentMorePage = MORE_ACCOUNTS;
                renderAppShell();
            }
        }));
        addSpace(10);
        content.addView(moreMenuCard("Categories", "Expense and income labels", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentMorePage = MORE_CATEGORIES;
                renderAppShell();
            }
        }));
        addSpace(10);
        content.addView(moreMenuCard("Borrow / Lend", "Open and settled money", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentMorePage = MORE_BORROW_LEND;
                renderAppShell();
            }
        }));
        addSpace(10);
        content.addView(moreMenuCard("Export CSV", "Download current records", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportTransactionsCsv();
            }
        }));
        addSpace(10);
        content.addView(moreMenuCard("Settings", "Available in a future update", null));
        addSpace(10);
        content.addView(moreMenuCard("Backup / Restore", "Available in a future update", null));
    }

    private void renderBorrowLendPage() {
        content.addView(subPageBack());
        content.addView(pageTitle("Borrow / Lend"));
        content.addView(pageSubtitle("Track open and completed borrowed or lent money."));
        addSpace(16);
        LinearLayout actions = horizontalRow();
        actions.addView(actionButton("Add Borrowed", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBorrowLendDialog(ExpenseDbHelper.TYPE_BORROWED);
            }
        }), weightParams());
        actions.addView(actionButton("Add Lended", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBorrowLendDialog(ExpenseDbHelper.TYPE_LENDED);
            }
        }), weightParams());
        content.addView(actions);

        addSpace(18);
        renderBorrowLendRecords();
    }

    private void renderTransactionFilters() {
        LinearLayout panel = panel();
        final EditText search = editText("Search transaction name");
        search.setText(transactionSearchText);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(6));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                transactionSearchText = s.toString().trim();
                refreshFilteredTransactions();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean pressedEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP;
                if (actionId == EditorInfo.IME_ACTION_SEARCH || pressedEnter) {
                    transactionSearchText = search.getText().toString().trim();
                    renderAppShell();
                    return true;
                }
                return false;
            }
        });

        LinearLayout searchRow = horizontalRow();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        searchParams.setMargins(0, 0, dp(8), 0);
        searchRow.addView(search, searchParams);
        searchRow.addView(filterIconButton(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transactionSearchText = search.getText().toString().trim();
                showTransactionFilterDialog();
            }
        }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(searchRow);
        addSpaceTo(panel, 8);
        panel.addView(filterSummary());

        content.addView(panel);
    }

    private void refreshFilteredTransactions() {
        if (transactionResultsContainer == null) {
            return;
        }
        transactionResultsContainer.removeAllViews();
        renderFilteredTransactions();
    }

    private void showTransactionFilterDialog() {
        LinearLayout form = dialogForm();

        final Spinner type = spinner(new String[]{"all", "expense", "income"});
        setSpinnerSelection(type, transactionFilterType);

        final Spinner accounts = optionSpinner(loadAccountsWithAll());
        setOptionSelection(accounts, transactionFilterAccountId);

        final Spinner categories = optionSpinner(loadCategoriesWithAll());
        setOptionSelection(categories, transactionFilterCategoryId);

        final Spinner timeline = spinner(new String[]{"all", "today", "weekly", "monthly"});
        setSpinnerSelection(timeline, transactionFilterTimeline);

        final Spinner sort = spinner(new String[]{"newest", "oldest", "amount_high", "amount_low"});
        setSpinnerSelection(sort, transactionSortOrder);

        form.addView(label("Type"));
        form.addView(type);
        form.addView(label("Account"));
        form.addView(accounts);
        form.addView(label("Category"));
        form.addView(categories);
        form.addView(label("Timeline"));
        form.addView(timeline);
        form.addView(label("Sort"));
        form.addView(sort);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Filter transactions")
                .setView(form)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Reset", null)
                .create();
        showDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transactionFilterType = (String) type.getSelectedItem();
                transactionFilterTimeline = (String) timeline.getSelectedItem();
                transactionSortOrder = (String) sort.getSelectedItem();
                transactionFilterAccountId = ((Option) accounts.getSelectedItem()).id;
                transactionFilterCategoryId = ((Option) categories.getSelectedItem()).id;
                dialog.dismiss();
                renderAppShell();
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transactionFilterType = "all";
                transactionFilterTimeline = "all";
                transactionSortOrder = "newest";
                transactionFilterAccountId = -1;
                transactionFilterCategoryId = -1;
                dialog.dismiss();
                renderAppShell();
            }
        });
    }

    private TextView filterSummary() {
        String summary = titleCase(transactionFilterType) + " · "
                + selectedOptionName(loadAccountsWithAll(), transactionFilterAccountId) + " · "
                + selectedOptionName(loadCategoriesWithAll(), transactionFilterCategoryId) + " · "
                + filterLabel(transactionFilterTimeline) + " · "
                + filterLabel(transactionSortOrder);
        if (!transactionSearchText.isEmpty()) {
            summary = "Search: " + transactionSearchText + " · " + summary;
        }
        TextView view = text(summary, 12, COLOR_MUTED, false);
        styleLabel(view);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private String selectedOptionName(List<Option> options, long id) {
        for (Option option : options) {
            if (option.id == id) {
                return option.label;
            }
        }
        return "All";
    }

    private String filterLabel(String value) {
        if ("amount_high".equals(value)) {
            return "Highest amount";
        }
        if ("amount_low".equals(value)) {
            return "Lowest amount";
        }
        return titleCase(value);
    }

    private void renderAccountsPage() {
        content.addView(subPageBack());
        content.addView(pageTitle("Accounts"));
        content.addView(pageSubtitle("Manage balances for cash, bank accounts, and wallets."));
        addSpace(16);
        Button addAccount = actionButton("Add Bank Account", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAccountDialog();
            }
        });
        content.addView(addAccount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addSpace(14);
        renderAccounts();
    }

    private void renderCategoriesPage() {
        content.addView(subPageBack());
        content.addView(pageTitle("Categories"));
        content.addView(pageSubtitle("Manage expense and income categories."));
        addSpace(16);
        Button addCategory = actionButton("Add Category", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCategoryDialog();
            }
        });
        content.addView(addCategory, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addSpace(14);
        renderCategories();
    }

    private void renderSummary() {
        long monthStart = startOfMonth();
        long monthIncome = db.getTotalForType(ExpenseDbHelper.TYPE_INCOME, monthStart, nextMonthStart());
        long monthExpense = db.getTotalForType(ExpenseDbHelper.TYPE_EXPENSE, monthStart, nextMonthStart());
        long todayIncome = db.getTotalForType(ExpenseDbHelper.TYPE_INCOME, startOfDay(), nextDayStart());
        long todaySpent = todayExpense();
        long todayNet = todayIncome - todaySpent;
        long monthNet = monthIncome - monthExpense;

        content.addView(accountBalancesPanel());

        addSpace(8);

        content.addView(summaryPanel("Today",
                new String[]{"Spent", "Income", "Net"},
                new String[]{ExpenseDbHelper.formatMoney(todaySpent), ExpenseDbHelper.formatMoney(todayIncome), signedMoney(todayNet)},
                new int[]{COLOR_RED, COLOR_GREEN, todayNet >= 0 ? COLOR_GREEN : COLOR_RED}));

        addSpace(8);

        content.addView(summaryPanel("This month",
                new String[]{"Income", "Spend", "Net"},
                new String[]{ExpenseDbHelper.formatMoney(monthIncome), ExpenseDbHelper.formatMoney(monthExpense), signedMoney(monthNet)},
                new int[]{COLOR_GREEN, COLOR_RED, monthNet >= 0 ? COLOR_GREEN : COLOR_RED}));
    }

    private LinearLayout accountBalancesPanel() {
        LinearLayout panel = panel();
        TextView heading = text("Remaining balance", 13, COLOR_NAVY_TEXT, true);
        styleTransactionName(heading);
        panel.addView(heading);
        addSpaceTo(panel, 6);

        long total = 0;
        Cursor cursor = db.getAccountsWithBalances();
        boolean hasAccounts = false;
        try {
            while (cursor.moveToNext()) {
                hasAccounts = true;
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                long balance = cursor.getLong(cursor.getColumnIndexOrThrow("balance"));
                total += balance;
                panel.addView(balanceRow(name, ExpenseDbHelper.formatMoney(balance), balance >= 0 ? COLOR_NAVY_TEXT : COLOR_RED, false));
            }
        } finally {
            cursor.close();
        }

        if (!hasAccounts) {
            panel.addView(text("No accounts added yet.", 12, COLOR_MUTED, false));
        }

        addSpaceTo(panel, 5);
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        panel.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        addSpaceTo(panel, 5);
        panel.addView(balanceRow("Total balance", ExpenseDbHelper.formatMoney(total), total >= 0 ? COLOR_GREEN : COLOR_RED, true));
        return panel;
    }

    private LinearLayout balanceRow(String label, String value, int valueColor, boolean total) {
        LinearLayout row = horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = text(label, total ? 13 : 12, total ? COLOR_NAVY_TEXT : COLOR_MUTED, total);
        styleLabel(left);
        TextView right = text(value, total ? 15 : 13, valueColor, true);
        styleTransactionAmount(right);
        right.setGravity(Gravity.RIGHT);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void renderWeeklyDashboardGraph() {
        final boolean showingExpense = dashboardWeeklyGraphShowsExpense;
        String type = showingExpense ? ExpenseDbHelper.TYPE_EXPENSE : ExpenseDbHelper.TYPE_INCOME;
        int accent = showingExpense ? COLOR_RED : COLOR_GREEN;
        int chartAccent = showingExpense ? COLOR_RED : COLOR_GREEN;
        long[] values = weeklyTotals(type);
        long total = 0;
        for (long value : values) {
            total += value;
        }

        LinearLayout panel = panel();
        LinearLayout header = horizontalRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("This week", 13, COLOR_NAVY_TEXT, true);
        styleTransactionName(title);
        TextView totalView = text((showingExpense ? "Spent " : "Income ") + ExpenseDbHelper.formatMoney(total), 12, accent, true);
        styleLabel(totalView);
        titleBlock.addView(title);
        titleBlock.addView(totalView);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout toggle = horizontalRow();
        toggle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        toggle.addView(weeklyToggleButton("Spent", true), weeklyToggleParams(false));
        toggle.addView(weeklyToggleButton("Income", false), weeklyToggleParams(true));
        header.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(header);
        addSpaceTo(panel, 10);

        WeeklyBarChartView chart = new WeeklyBarChartView(this, values, weeklyDayLabels(), chartAccent);
        chart.setBackground(tileBackground(Color.WHITE, COLOR_BORDER));
        panel.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        content.addView(panel);
    }

    private TextView weeklyToggleButton(String label, final boolean expense) {
        boolean selected = dashboardWeeklyGraphShowsExpense == expense;
        TextView button = text(label, 11, selected ? Color.WHITE : COLOR_MUTED, true);
        styleButton(button);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), dp(5), dp(9), dp(5));
        button.setBackground(tileBackground(selected ? (expense ? COLOR_RED : COLOR_GREEN) : COLOR_FIELD,
                selected ? Color.TRANSPARENT : COLOR_BORDER));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dashboardWeeklyGraphShowsExpense = expense;
                renderAppShell();
            }
        });
        return button;
    }

    private LinearLayout.LayoutParams weeklyToggleParams(boolean hasLeftGap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (hasLeftGap) {
            params.setMargins(dp(6), 0, 0, 0);
        }
        return params;
    }

    private long[] weeklyTotals(String type) {
        long[] values = new long[7];
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(startOfWeek());
        for (int index = 0; index < values.length; index++) {
            long start = day.getTimeInMillis();
            day.add(Calendar.DAY_OF_YEAR, 1);
            values[index] = db.getTotalForType(type, start, day.getTimeInMillis());
        }
        return values;
    }

    private String[] weeklyDayLabels() {
        String[] labels = new String[7];
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(startOfWeek());
        SimpleDateFormat format = new SimpleDateFormat("EEE", Locale.US);
        for (int index = 0; index < labels.length; index++) {
            labels[index] = format.format(day.getTime());
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return labels;
    }

    private void renderQuickTransactionActions() {
        LinearLayout first = horizontalRow();
        first.addView(actionButton("Add Expense", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTransactionDialog(ExpenseDbHelper.TYPE_EXPENSE);
            }
        }), weightParams());
        first.addView(actionButton("Add Income", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTransactionDialog(ExpenseDbHelper.TYPE_INCOME);
            }
        }), weightParams());
        content.addView(first);
    }

    private void renderInsights() {
        long weekStart = startOfWeek();
        long weekEnd = nextWeekStart();
        long monthStart = startOfMonth();
        long monthEnd = nextMonthStart();
        long weekIncome = db.getTotalForType(ExpenseDbHelper.TYPE_INCOME, weekStart, weekEnd);
        long weekExpense = db.getTotalForType(ExpenseDbHelper.TYPE_EXPENSE, weekStart, weekEnd);
        long monthExpense = db.getTotalForType(ExpenseDbHelper.TYPE_EXPENSE, monthStart, monthEnd);
        long weekNet = weekIncome - weekExpense;
        String topCategory = db.getTopExpenseCategory(monthStart, monthEnd);

        LinearLayout panel = panel();
        TextView heading = text("Insights", 15, COLOR_NAVY_TEXT, true);
        styleSectionHeading(heading);
        panel.addView(heading);
        addSpaceTo(panel, 8);
        addDashboardInsightRow(panel, "This week net",
                signedMoney(weekNet) + " · " + db.getTransactionCount(weekStart, weekEnd) + " txns",
                weekNet >= 0 ? COLOR_GREEN : COLOR_RED);
        addDashboardInsightRow(panel, "This month",
                ExpenseDbHelper.formatMoney(monthExpense) + " spent",
                COLOR_NAVY_TEXT);
        addDashboardInsightRow(panel, "Top category", topCategory, COLOR_NAVY_TEXT);
        content.addView(panel);
    }

    private void renderPeriodAnalytics() {
        content.addView(sectionTitle("Analytics"));
        addPeriodCard("Today", startOfDay(), nextDayStart());
        addSpace(10);
        addPeriodCard("This Week", startOfWeek(), nextWeekStart());
        addSpace(10);
        addPeriodCard("This Month", startOfMonth(), nextMonthStart());
    }

    private void addPeriodCard(String title, long startInclusive, long endExclusive) {
        long income = db.getTotalForType(ExpenseDbHelper.TYPE_INCOME, startInclusive, endExclusive);
        long expense = db.getTotalForType(ExpenseDbHelper.TYPE_EXPENSE, startInclusive, endExclusive);
        long net = income - expense;
        int count = db.getTransactionCount(startInclusive, endExclusive);
        long max = Math.max(income, expense);

        LinearLayout panel = panel();
        TextView heading = text(title, 17, Color.rgb(16, 24, 40), true);
        styleSectionHeading(heading);
        panel.addView(heading);
        TextView meta = text(count + " transactions | Net " + signedMoney(net), 13, Color.rgb(71, 84, 103), false);
        styleLabel(meta);
        panel.addView(meta);
        addSpaceTo(panel, 8);
        panel.addView(compactMetricRow("Income", ExpenseDbHelper.formatMoney(income), COLOR_GREEN));
        panel.addView(progressBar(income, max, COLOR_GREEN));
        addSpaceTo(panel, 6);
        panel.addView(compactMetricRow("Expense", ExpenseDbHelper.formatMoney(expense), COLOR_RED));
        panel.addView(progressBar(expense, max, COLOR_RED));
        addSpaceTo(panel, 6);
        panel.addView(compactMetricRow("Net", signedMoney(net), net >= 0 ? COLOR_GREEN : COLOR_RED));
        content.addView(panel);
    }

    private void renderCategoryBreakdown() {
        content.addView(sectionTitle("Monthly Category Breakdown"));
        LinearLayout panel = panel();
        long monthStart = startOfMonth();
        addCategoryRows(panel, "Expense", ExpenseDbHelper.TYPE_EXPENSE, monthStart, nextMonthStart(), 0);
        addSpaceTo(panel, 10);
        addCategoryRows(panel, "Income", ExpenseDbHelper.TYPE_INCOME, monthStart, nextMonthStart(), 0);
        content.addView(panel);
    }

    private long addCategoryRows(LinearLayout panel, String title, String type, long startInclusive, long endExclusive, long sharedMax) {
        TextView heading = text(title, 17, Color.rgb(16, 24, 40), true);
        styleSectionHeading(heading);
        panel.addView(heading);
        Cursor cursor = db.getCategoryTotals(type, startInclusive, endExclusive, 5);
        long max = sharedMax;
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Long> totals = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow("total"));
                names.add(name);
                totals.add(total);
                max = Math.max(max, total);
            }
        } finally {
            cursor.close();
        }
        if (names.isEmpty()) {
            panel.addView(text("No " + type + " categories this month.", 14, Color.rgb(102, 112, 133), false));
            return max;
        }
        for (int index = 0; index < names.size(); index++) {
            int color = ExpenseDbHelper.TYPE_EXPENSE.equals(type) ? COLOR_RED : COLOR_GREEN;
            panel.addView(compactMetricRow(names.get(index), ExpenseDbHelper.formatMoney(totals.get(index)), color));
            panel.addView(progressBar(totals.get(index), max, color));
            addSpaceTo(panel, 6);
        }
        return max;
    }

    private void renderAccountActivity() {
        content.addView(sectionTitle("Monthly Account Activity"));
        LinearLayout panel = panel();
        Cursor cursor = db.getAccountActivityTotals(startOfMonth(), nextMonthStart(), 5);
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(text("No account movement this month.", 14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    long income = cursor.getLong(cursor.getColumnIndexOrThrow("income_total"));
                    long expense = cursor.getLong(cursor.getColumnIndexOrThrow("expense_total"));
                    TextView accountName = text(name, 16, Color.rgb(16, 24, 40), false);
                    styleTransactionName(accountName);
                    panel.addView(accountName);
                    panel.addView(text("In " + ExpenseDbHelper.formatMoney(income) + " | Out " +
                            ExpenseDbHelper.formatMoney(expense) + " | Net " + signedMoney(income - expense),
                            14, Color.rgb(52, 64, 84), false));
                    addSpaceTo(panel, 8);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        content.addView(panel);
    }

    private void renderAccounts() {
        content.addView(sectionTitle("Bank Accounts"));
        LinearLayout panel = panel();
        Cursor cursor = db.getAccountsWithBalances();
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(text("Add your first bank account to start tracking.", 14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    final long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    long balance = cursor.getLong(cursor.getColumnIndexOrThrow("balance"));
                    TextView account = text(name + "  " + ExpenseDbHelper.formatMoney(balance), 16, Color.rgb(16, 24, 40), true);
                    styleTransactionAmount(account);
                    account.setPadding(0, dp(8), 0, dp(8));
                    account.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showEditAccountDialog(id);
                        }
                    });
                    panel.addView(account);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        content.addView(panel);
    }

    private void renderCategories() {
        content.addView(sectionTitle("Categories"));
        LinearLayout panel = panel();
        Cursor cursor = db.getAllCategories();
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(text("No categories yet.", 14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    final long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    final String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    TextView category = text(name + "  (" + type + ")", 16, Color.rgb(16, 24, 40), false);
                    styleTransactionName(category);
                    category.setPadding(0, dp(8), 0, dp(8));
                    category.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showEditCategoryDialog(id);
                        }
                    });
                    panel.addView(category);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        content.addView(panel);
    }

    private void renderBorrowLendRecords() {
        content.addView(sectionTitle("Borrowed / Lended"));
        LinearLayout panel = panel();
        Cursor cursor = db.getBorrowLendRecords();
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(text("No borrowed or lent records yet.", 14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    final long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    long amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
                    int completed = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed"));
                    String account = cursor.getString(cursor.getColumnIndexOrThrow("account"));
                    String status = completed == 1
                            ? (ExpenseDbHelper.TYPE_BORROWED.equals(type) ? "Repaid" : "Gained")
                            : "Open";
                    String action = completed == 1
                            ? ""
                            : "\nTap to mark " + (ExpenseDbHelper.TYPE_BORROWED.equals(type) ? "repaid" : "gained");

                    TextView item = text(
                            titleCase(type) + "  " + ExpenseDbHelper.formatMoney(amount) + "  " + status + "\n" +
                                    name + " | " + account + action,
                            15,
                            completed == 1 ? Color.rgb(71, 84, 103) :
                                    (ExpenseDbHelper.TYPE_BORROWED.equals(type) ? COLOR_RED : COLOR_GREEN),
                            true);
                    item.setPadding(0, dp(8), 0, dp(8));
                    if (completed == 0) {
                        item.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                confirmCompleteBorrowLend(id, type);
                            }
                        });
                    }
                    panel.addView(item);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        content.addView(panel);
    }

    private void renderFilteredTransactions() {
        LinearLayout parent = transactionResultsContainer == null ? content : transactionResultsContainer;
        parent.addView(sectionTitle("Transactions"));
        LinearLayout panel = panel();
        long[] range = timelineRange(transactionFilterTimeline);
        Cursor cursor = db.getFilteredTransactions(
                transactionFilterType,
                transactionFilterAccountId,
                transactionFilterCategoryId,
                range[0],
                range[1],
                transactionSearchText,
                transactionSortOrder,
                100);
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(text("No transactions match these filters.", 14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    final long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    long amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow("transaction_date"));
                    String category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
                    String account = cursor.getString(cursor.getColumnIndexOrThrow("account"));

                    panel.addView(transactionRow(id, name, type, amount, date, category, account));
                    addSpaceTo(panel, 8);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        parent.addView(panel);
    }

    private void renderRecentTransactions(int limit) {
        content.addView(sectionTitle("Recent Transactions"));
        LinearLayout panel = panel();
        Cursor cursor = db.getRecentTransactions(limit);
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(text("No transactions yet.", 14, Color.rgb(102, 112, 133), false));
            } else {
                do {
                    final long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    long amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow("transaction_date"));
                    String category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
                    String account = cursor.getString(cursor.getColumnIndexOrThrow("account"));

                    panel.addView(transactionRow(id, name, type, amount, date, category, account));
                    addSpaceTo(panel, 8);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        content.addView(panel);
    }

    private void showAccountDialog() {
        LinearLayout form = transactionDialogForm("Add bank account", "Create a cash, bank, or wallet balance.");
        final EditText name = editText("Account name");
        final EditText balance = editText("Opening balance");
        balance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        addDialogField(form, "Account name", name);
        addDialogField(form, "Opening balance", balance);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String accountName = required(name, "Account name");
                long opening = balance.getText().toString().trim().isEmpty() ? 0 : ExpenseDbHelper.parseMoney(balance.getText().toString());
                db.addBankAccount(accountName, opening);
                dialog.dismiss();
                refreshAfterDataChange();
            } catch (Exception ex) {
                toast(ex.getMessage());
            }
        }));
        showDialog(dialog);
        styleDialogWindow(dialog);
    }

    private void showEditAccountDialog(long accountId) {
        Cursor cursor = db.getBankAccount(accountId);
        try {
            if (!cursor.moveToFirst()) {
                toast("Account not found.");
                return;
            }

            LinearLayout form = transactionDialogForm("Edit bank account", "Update the account name or starting balance.");
            final EditText name = editText("Account name");
            final EditText balance = editText("Opening balance");
            balance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            name.setText(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            balance.setText(moneyInput(cursor.getLong(cursor.getColumnIndexOrThrow("opening_balance"))));
            addDialogField(form, "Account name", name);
            addDialogField(form, "Opening balance", balance);

            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(form)
                    .setPositiveButton("Save", null)
                    .setNegativeButton("Cancel", null)
                    .create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String accountName = required(name, "Account name");
                    long opening = balance.getText().toString().trim().isEmpty() ? 0 : ExpenseDbHelper.parseMoney(balance.getText().toString());
                    db.updateBankAccount(accountId, accountName, opening);
                    dialog.dismiss();
                    refreshAfterDataChange();
                } catch (Exception ex) {
                    toast(ex.getMessage());
                }
            }));
            showDialog(dialog);
            styleDialogWindow(dialog);
        } finally {
            cursor.close();
        }
    }

    private void showCategoryDialog() {
        LinearLayout form = transactionDialogForm("Add category", "Organize transactions by expense or income type.");
        final EditText name = editText("Category name");
        final Spinner type = spinner(new String[]{"expense", "income", "both"});
        addDialogField(form, "Category name", name);
        addDialogField(form, "Category type", type);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                db.addCategory(required(name, "Category name"), (String) type.getSelectedItem());
                dialog.dismiss();
                refreshAfterDataChange();
            } catch (Exception ex) {
                toast(ex.getMessage());
            }
        }));
        showDialog(dialog);
        styleDialogWindow(dialog);
    }

    private void showEditCategoryDialog(long categoryId) {
        Cursor cursor = db.getCategory(categoryId);
        try {
            if (!cursor.moveToFirst()) {
                toast("Category not found.");
                return;
            }

            LinearLayout form = transactionDialogForm("Edit category", "Rename the category or change where it appears.");
            final EditText name = editText("Category name");
            final Spinner type = spinner(new String[]{"expense", "income", "both"});
            name.setText(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            setSpinnerSelection(type, cursor.getString(cursor.getColumnIndexOrThrow("type")));
            addDialogField(form, "Category name", name);
            addDialogField(form, "Category type", type);

            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(form)
                    .setPositiveButton("Save", null)
                    .setNegativeButton("Cancel", null)
                    .create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    db.updateCategory(categoryId, required(name, "Category name"), (String) type.getSelectedItem());
                    dialog.dismiss();
                    refreshAfterDataChange();
                } catch (Exception ex) {
                    toast(ex.getMessage());
                }
            }));
            showDialog(dialog);
            styleDialogWindow(dialog);
        } finally {
            cursor.close();
        }
    }

    private void showBorrowLendDialog(String borrowType) {
        if (!db.hasAccounts()) {
            toast("Add a bank account first.");
            showAccountDialog();
            return;
        }

        final List<Option> accountOptions = loadAccounts();
        String title = ExpenseDbHelper.TYPE_BORROWED.equals(borrowType) ? "Add borrowed money" : "Add lent money";
        LinearLayout form = transactionDialogForm(title,
                ExpenseDbHelper.TYPE_BORROWED.equals(borrowType)
                        ? "Track money you received and need to return."
                        : "Track money you gave and need to collect.");
        final EditText name = editText("Friend name");
        final EditText amount = editText("Amount");
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final Spinner accounts = optionSpinner(accountOptions);

        addDialogField(form, "Person", name);
        addDialogField(form, "Amount", amount);
        addDialogField(form, "Bank account", accounts);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                db.addBorrowLend(
                        required(name, "Friend name"),
                        ExpenseDbHelper.parseMoney(required(amount, "Amount")),
                        borrowType,
                        ((Option) accounts.getSelectedItem()).id);
                dialog.dismiss();
                refreshAfterDataChange();
            } catch (Exception ex) {
                toast(ex.getMessage());
            }
        }));
        showDialog(dialog);
        styleDialogWindow(dialog);
    }

    private void showAddTransactionDialog() {
        showTransactionEditorDialog(-1, ExpenseDbHelper.TYPE_EXPENSE, "", 0, -1, -1, System.currentTimeMillis());
    }

    private void showTransactionDialog(String transactionType) {
        showTransactionEditorDialog(-1, transactionType, "", 0, -1, -1, System.currentTimeMillis());
    }

    private void showEditTransactionDialog(long transactionId) {
        Cursor cursor = db.getTransaction(transactionId);
        try {
            if (!cursor.moveToFirst()) {
                toast("Transaction not found.");
                return;
            }
            int borrowLendColumn = cursor.getColumnIndexOrThrow("borrow_lend_id");
            if (!cursor.isNull(borrowLendColumn)) {
                toast("Manage this transaction from Borrow / Lend.");
                return;
            }
            showTransactionEditorDialog(
                    transactionId,
                    cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("amount")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("category_id")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("bank_account_id")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("transaction_date")));
        } finally {
            cursor.close();
        }
    }

    private void showTransactionEditorDialog(final long transactionId, String initialType, String initialName,
                                             long initialAmount, final long initialCategoryId,
                                             long initialAccountId, long initialDate) {
        if (!db.hasAccounts()) {
            toast("Add a bank account first.");
            showAccountDialog();
            return;
        }

        final List<Option> accountOptions = loadAccounts();
        final ArrayList<Option> categoryOptions = new ArrayList<>(loadCategories(initialType));
        if (categoryOptions.isEmpty() && loadCategories(alternateTransactionType(initialType)).isEmpty()) {
            toast("Add a category first.");
            showCategoryDialog();
            return;
        }

        final boolean editing = transactionId > 0;
        LinearLayout form = transactionDialogForm(
                editing ? "Edit transaction" :
                        (ExpenseDbHelper.TYPE_EXPENSE.equals(initialType) ? "Add expense" : "Add income"),
                editing ? "Update the details below." : "Add a new record to your accounts.");
        final Spinner type = spinner(new String[]{ExpenseDbHelper.TYPE_EXPENSE, ExpenseDbHelper.TYPE_INCOME});
        setSpinnerSelection(type, initialType);
        final EditText name = editText("Name");
        name.setText(initialName);
        final EditText amount = editText("Amount");
        if (initialAmount > 0) {
            amount.setText(moneyInput(initialAmount));
        }
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final ArrayAdapter<Option> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categoryOptions);
        final Spinner categories = new Spinner(this);
        categories.setAdapter(categoryAdapter);
        styleSpinner(categories);
        setOptionSelection(categories, initialCategoryId);
        final Spinner accounts = optionSpinner(accountOptions);
        setOptionSelection(accounts, initialAccountId);
        final Calendar selectedDate = Calendar.getInstance();
        selectedDate.setTimeInMillis(initialDate);
        final Button dateButton = actionButton(dateTimeLabel(selectedDate.getTimeInMillis()), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDateTimePicker(selectedDate, (Button) v);
            }
        });
        dateButton.setMinHeight(dp(46));
        dateButton.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_my_calendar, 0, 0, 0);
        dateButton.setCompoundDrawablePadding(dp(8));

        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                long selectedCategoryId = categories.getSelectedItem() instanceof Option
                        ? ((Option) categories.getSelectedItem()).id
                        : initialCategoryId;
                categoryOptions.clear();
                categoryOptions.addAll(loadCategories((String) type.getSelectedItem()));
                categoryAdapter.notifyDataSetChanged();
                setOptionSelection(categories, selectedCategoryId);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        addDialogField(form, "Type", type);
        addDialogField(form, "Transaction name", name);
        addDialogField(form, "Amount", amount);
        addDialogField(form, "Date and time", dateButton);
        addDialogField(form, "Category", categories);
        addDialogField(form, "Bank account", accounts);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(form)
                .setPositiveButton(editing ? "Update" : "Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                if (categoryAdapter.getCount() == 0) {
                    throw new IllegalArgumentException("Add a category for this transaction type first.");
                }
                if (editing) {
                    db.updateTransaction(
                            transactionId,
                            required(name, "Name"),
                            ExpenseDbHelper.parseMoney(required(amount, "Amount")),
                            (String) type.getSelectedItem(),
                            ((Option) categories.getSelectedItem()).id,
                            ((Option) accounts.getSelectedItem()).id,
                            selectedDate.getTimeInMillis());
                } else {
                    db.addTransaction(
                            required(name, "Name"),
                            ExpenseDbHelper.parseMoney(required(amount, "Amount")),
                            (String) type.getSelectedItem(),
                            ((Option) categories.getSelectedItem()).id,
                            ((Option) accounts.getSelectedItem()).id,
                            selectedDate.getTimeInMillis());
                }
                dialog.dismiss();
                refreshAfterDataChange();
            } catch (Exception ex) {
                toast(ex.getMessage());
            }
        }));
        showDialog(dialog);
        styleDialogWindow(dialog);
    }

    private String alternateTransactionType(String type) {
        return ExpenseDbHelper.TYPE_EXPENSE.equals(type) ? ExpenseDbHelper.TYPE_INCOME : ExpenseDbHelper.TYPE_EXPENSE;
    }

    private void showDateTimePicker(final Calendar selectedDate, final Button dateButton) {
        android.app.DatePickerDialog dateDialog = new android.app.DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    android.app.TimePickerDialog timeDialog = new android.app.TimePickerDialog(
                            MainActivity.this,
                            (timeView, hourOfDay, minute) -> {
                                selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                selectedDate.set(Calendar.MINUTE, minute);
                                selectedDate.set(Calendar.SECOND, 0);
                                selectedDate.set(Calendar.MILLISECOND, 0);
                                dateButton.setText(dateTimeLabel(selectedDate.getTimeInMillis()));
                            },
                            selectedDate.get(Calendar.HOUR_OF_DAY),
                            selectedDate.get(Calendar.MINUTE),
                            false);
                    timeDialog.show();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH));
        dateDialog.show();
    }

    private String dateTimeLabel(long date) {
        return shortDateFormat.format(new Date(date)) + ", " + timeFormat.format(new Date(date));
    }

    private void confirmDeleteTransaction(final long id) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete transaction?")
                .setMessage("This will remove it from your local records.")
                .setPositiveButton("Delete", (ignoredDialog, which) -> {
                    db.deleteTransaction(id);
                    refreshAfterDataChange();
                })
                .setNegativeButton("Cancel", null)
                .create();
        showDialog(dialog);
    }

    private void confirmCompleteBorrowLend(final long id, String type) {
        String action = ExpenseDbHelper.TYPE_BORROWED.equals(type) ? "repaid" : "gained";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Mark as " + action + "?")
                .setMessage("This will complete the borrow/lend record and update the account balance.")
                .setPositiveButton(titleCase(action), (ignoredDialog, which) -> {
                    db.completeBorrowLend(id);
                    refreshAfterDataChange();
                })
                .setNegativeButton("Cancel", null)
                .create();
        showDialog(dialog);
    }

    private LinearLayout transactionRow(final long id, String name, String type, long amount, long date, String category, String account) {
        boolean expense = ExpenseDbHelper.TYPE_EXPENSE.equals(type);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(9), dp(6), dp(9), dp(6));
        item.setBackground(tileBackground(Color.WHITE, Color.TRANSPARENT));
        item.setMinimumHeight(dp(50));
        applyCardElevation(item);

        item.addView(categoryBadge(category, expense), badgeParams());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = text(name, 14, COLOR_NAVY_TEXT, false);
        styleTransactionName(nameView);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);

        LinearLayout metaLine = horizontalRow();
        metaLine.setGravity(Gravity.CENTER_VERTICAL);
        metaLine.addView(transactionMetaColumn(cleanMetaText(category, "Category"), Gravity.LEFT), metaColumnParams(1.15f));
        metaLine.addView(transactionMetaColumn(cleanMetaText(account, "Account"), Gravity.CENTER), metaColumnParams(1.0f));
        metaLine.addView(transactionMetaColumn(transactionDateText(date), Gravity.RIGHT), metaColumnParams(1.0f));

        body.addView(nameView);
        body.addView(metaLine);
        item.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView amountView = text((expense ? "-" : "+") + ExpenseDbHelper.formatMoney(amount),
                14,
                expense ? COLOR_RED : COLOR_GREEN,
                true);
        styleTransactionAmount(amountView);
        amountView.setGravity(Gravity.RIGHT);
        amountView.setSingleLine(true);
        amountView.setEllipsize(TextUtils.TruncateAt.END);

        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT);
        amountParams.setMargins(dp(8), 0, 0, 0);
        item.addView(amountView, amountParams);

        TextView arrow = text(">", 13, COLOR_MUTED, true);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(circleBackground(Color.WHITE, COLOR_BORDER));
        applyCardElevation(arrow);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        arrowParams.setMargins(dp(5), 0, 0, 0);
        item.addView(arrow, arrowParams);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditTransactionDialog(id);
            }
        });
        item.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDeleteTransaction(id);
                return true;
            }
        });
        return item;
    }

    private TextView categoryBadge(String category, boolean expense) {
        TextView badge = text(categoryInitial(category), 11, expense ? Color.rgb(181, 71, 8) : Color.rgb(5, 122, 85), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(tileBackground(expense ? Color.rgb(255, 244, 229) : Color.rgb(230, 248, 240), Color.TRANSPARENT));
        return badge;
    }

    private LinearLayout.LayoutParams badgeParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(30), dp(30));
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private TextView transactionMetaColumn(String value, int gravity) {
        TextView view = text(value, 10, COLOR_MUTED, false);
        styleTransactionMeta(view);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(gravity);
        return view;
    }

    private LinearLayout.LayoutParams metaColumnParams(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private String categoryInitial(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "-";
        }
        return category.trim().substring(0, 1).toUpperCase(Locale.US);
    }

    private String transactionDateText(long date) {
        Calendar tx = Calendar.getInstance();
        tx.setTimeInMillis(date);
        Calendar today = Calendar.getInstance();
        boolean sameDay = tx.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && tx.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        return (sameDay ? "Today" : shortDateFormat.format(new Date(date))) + " " + compactTimeText(date);
    }

    private String compactTimeText(long date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        int hour = calendar.get(Calendar.HOUR);
        if (hour == 0) {
            hour = 12;
        }
        return hour + (calendar.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
    }

    private String cleanMetaText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private long[] timelineRange(String timeline) {
        if ("today".equals(timeline)) {
            long start = startOfDay();
            return new long[]{start, nextDayStart()};
        }
        if ("weekly".equals(timeline)) {
            return new long[]{startOfWeek(), nextWeekStart()};
        }
        if ("monthly".equals(timeline)) {
            return new long[]{startOfMonth(), nextMonthStart()};
        }
        return new long[]{-1, -1};
    }

    private void exportTransactionsCsv() {
        long[] range = timelineRange(transactionFilterTimeline);
        Cursor cursor = db.getFilteredTransactions(
                transactionFilterType,
                transactionFilterAccountId,
                transactionFilterCategoryId,
                range[0],
                range[1],
                transactionSearchText,
                transactionSortOrder,
                10000);
        FileWriter writer = null;
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) {
                dir = getFilesDir();
            }
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Could not create export folder.");
            }
            File file = new File(dir, "personal-os-transactions.csv");
            writer = new FileWriter(file, false);
            writer.write("date,type,name,amount,category,account\n");
            SimpleDateFormat csvDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            while (cursor.moveToNext()) {
                writer.write(csv(csvDate.format(new Date(cursor.getLong(cursor.getColumnIndexOrThrow("transaction_date"))))));
                writer.write(",");
                writer.write(csv(cursor.getString(cursor.getColumnIndexOrThrow("type"))));
                writer.write(",");
                writer.write(csv(cursor.getString(cursor.getColumnIndexOrThrow("name"))));
                writer.write(",");
                writer.write(csv(moneyInput(cursor.getLong(cursor.getColumnIndexOrThrow("amount")))));
                writer.write(",");
                writer.write(csv(cursor.getString(cursor.getColumnIndexOrThrow("category"))));
                writer.write(",");
                writer.write(csv(cursor.getString(cursor.getColumnIndexOrThrow("account"))));
                writer.write("\n");
            }
            toast("CSV exported: " + file.getAbsolutePath());
        } catch (Exception ex) {
            toast("Export failed: " + ex.getMessage());
        } finally {
            cursor.close();
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String csv(String value) {
        if (value == null) {
            value = "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private List<Option> loadCategories(String type) {
        ArrayList<Option> options = new ArrayList<>();
        Cursor cursor = db.getCategoriesForType(type);
        try {
            while (cursor.moveToNext()) {
                options.add(new Option(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name"))));
            }
        } finally {
            cursor.close();
        }
        return options;
    }

    private List<Option> loadCategoriesWithAll() {
        ArrayList<Option> options = new ArrayList<>();
        options.add(new Option(-1, "All categories"));
        Cursor cursor = db.getAllCategories();
        try {
            while (cursor.moveToNext()) {
                options.add(new Option(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name"))));
            }
        } finally {
            cursor.close();
        }
        return options;
    }

    private List<Option> loadAccounts() {
        ArrayList<Option> options = new ArrayList<>();
        Cursor cursor = db.getAccountsWithBalances();
        try {
            while (cursor.moveToNext()) {
                options.add(new Option(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name"))));
            }
        } finally {
            cursor.close();
        }
        return options;
    }

    private List<Option> loadAccountsWithAll() {
        ArrayList<Option> options = new ArrayList<>();
        options.add(new Option(-1, "All accounts"));
        Cursor cursor = db.getAccountsWithBalances();
        try {
            while (cursor.moveToNext()) {
                options.add(new Option(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name"))));
            }
        } finally {
            cursor.close();
        }
        return options;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    private void scheduleDailySummary() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(this, DailySummaryReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 20);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent);
    }

    private void addAnalyticsSummaryPanel(LinearLayout parent, String title, String[] labels, String[] values) {
        LinearLayout summary = panel();
        addPanelTitle(summary, title);
        addInlineSummaryGrid(summary, labels, values);
        parent.addView(summary);
    }

    private void addPanelTitle(LinearLayout panel, String title) {
        panel.addView(sectionTitle(title));
        addSpaceTo(panel, 4);
    }

    private void addInlineSummaryGrid(LinearLayout parent, String[] labels, String[] values) {
        for (int index = 0; index < labels.length; index += 2) {
            if (index + 1 >= labels.length) {
                parent.addView(inlineSummaryMetric(labels[index], values[index]), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                LinearLayout row = horizontalRow();
                row.addView(inlineSummaryMetric(labels[index], values[index]), weightParams());
                row.addView(inlineSummaryMetric(labels[index + 1], values[index + 1]), weightParams());
                parent.addView(row);
            }
            if (index + 2 < labels.length) {
                addSpaceTo(parent, 10);
            }
        }
    }

    private LinearLayout inlineSummaryMetric(String label, String value) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setPadding(dp(4), dp(2), dp(4), dp(4));
        metric.setMinimumHeight(dp(54));

        TextView labelView = text(label, 12, COLOR_MUTED, false);
        styleLabel(labelView);
        TextView valueView = text(value, 17, metricValueColor(label, value), true);
        styleCardAmount(valueView);
        valueView.setPadding(0, dp(4), 0, 0);

        metric.addView(labelView);
        metric.addView(valueView);
        return metric;
    }

    private LinearLayout emptyPanel(String message) {
        LinearLayout panel = panel();
        panel.addView(text(message, 12, COLOR_MUTED, false));
        return panel;
    }

    private void renderDailySpendingTrend(LinearLayout parent, long startInclusive, long endExclusive) {
        LinearLayout panel = panel();
        addPanelTitle(panel, "Daily Spending Trend");
        int days = Math.max(1, (int) ((endExclusive - startInclusive) / (24L * 60L * 60L * 1000L)));
        long[] expenses = new long[days];
        Cursor cursor = db.getTransactionsBetween(startInclusive, endExclusive);
        try {
            while (cursor.moveToNext()) {
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                if (!ExpenseDbHelper.TYPE_EXPENSE.equals(type)) {
                    continue;
                }
                long date = cursor.getLong(cursor.getColumnIndexOrThrow("transaction_date"));
                int index = bucketIndex(date, startInclusive, days);
                if (index >= 0) {
                    expenses[index] += cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
                }
            }
        } finally {
            cursor.close();
        }

        panel.addView(new SpendingTrendChartView(this, expenses),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
        long total = 0;
        long highest = 0;
        int highestDay = 0;
        for (int index = 0; index < expenses.length; index++) {
            total += expenses[index];
            if (expenses[index] > highest) {
                highest = expenses[index];
                highestDay = index;
            }
        }
        panel.addView(compactMetricRow("Month spend so far", ExpenseDbHelper.formatMoney(total), COLOR_RED));
        panel.addView(compactMetricRow("Highest spend day", "Day " + (highestDay + 1) + " | " + ExpenseDbHelper.formatMoney(highest), COLOR_RED));
        parent.addView(panel);
    }

    private void renderCashflowChart(LinearLayout parent, String title, long startInclusive, long endExclusive, int bucketCount) {
        LinearLayout panel = panel();
        addPanelTitle(panel, title);
        bucketCount = Math.max(1, bucketCount);
        long[] income = new long[bucketCount];
        long[] expense = new long[bucketCount];
        Cursor cursor = db.getTransactionsBetween(startInclusive, endExclusive);
        try {
            while (cursor.moveToNext()) {
                long date = cursor.getLong(cursor.getColumnIndexOrThrow("transaction_date"));
                int index = bucketIndex(date, startInclusive, bucketCount);
                if (index < 0) {
                    continue;
                }
                long amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                if (ExpenseDbHelper.TYPE_INCOME.equals(type)) {
                    income[index] += amount;
                } else if (ExpenseDbHelper.TYPE_EXPENSE.equals(type)) {
                    expense[index] += amount;
                }
            }
        } finally {
            cursor.close();
        }

        panel.addView(new CashflowBarChartView(this, income, expense),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
        long incomeTotal = sum(income);
        long expenseTotal = sum(expense);
        panel.addView(compactMetricRow("Income", ExpenseDbHelper.formatMoney(incomeTotal), COLOR_GREEN));
        panel.addView(compactMetricRow("Expense", ExpenseDbHelper.formatMoney(expenseTotal), COLOR_RED));
        panel.addView(compactMetricRow("Net cashflow", signedMoney(incomeTotal - expenseTotal),
                incomeTotal >= expenseTotal ? COLOR_GREEN : COLOR_RED));
        parent.addView(panel);
    }

    private void renderAccountTrendOverview(LinearLayout parent, long startInclusive, long endExclusive) {
        LinearLayout panel = panel();
        addPanelTitle(panel, "Account Trends");
        int weekCount = monthWeekCount(startInclusive, endExclusive);
        ArrayList<AccountTrend> trends = new ArrayList<>();
        Cursor cursor = db.getTransactionsBetween(startInclusive, endExclusive);
        try {
            while (cursor.moveToNext()) {
                String account = cursor.getString(cursor.getColumnIndexOrThrow("account"));
                AccountTrend trend = findAccountTrend(trends, account);
                long amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                long signed = ExpenseDbHelper.TYPE_INCOME.equals(type) ? amount : -amount;
                long date = cursor.getLong(cursor.getColumnIndexOrThrow("transaction_date"));
                int week = Math.min(weekCount - 1, Math.max(0, (int) ((date - startInclusive) / (7L * 24L * 60L * 60L * 1000L))));
                trend.weeklyNet[week] += signed;
                trend.totalNet += signed;
            }
        } finally {
            cursor.close();
        }

        if (trends.isEmpty()) {
            panel.addView(text("No account movement this month.", 12, COLOR_MUTED, false));
        } else {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, dp(4), 0);
            for (AccountTrend trend : trends) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        dp(292), ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, dp(10), 0);
                row.addView(accountTrendCard(trend, weekCount), params);
            }
            scroll.addView(row, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            panel.addView(scroll);
        }
        parent.addView(panel);
    }

    private LinearLayout accountTrendCard(AccountTrend trend, int weekCount) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackground(tileBackground(COLOR_FIELD, COLOR_BORDER));

        LinearLayout header = horizontalRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView account = text(trend.account, 14, COLOR_NAVY_TEXT, true);
        styleTransactionName(account);
        TextView total = text(signedMoney(trend.totalNet), 13,
                trend.totalNet >= 0 ? COLOR_GREEN : COLOR_RED, true);
        styleTransactionAmount(total);
        total.setGravity(Gravity.RIGHT);
        header.addView(account, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(total, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(header);

        addSpaceTo(card, 8);
        LinearLayout weeks = horizontalRow();
        weeks.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
        for (int index = 0; index < weekCount; index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(dp(2), 0, dp(2), 0);
            weeks.addView(accountWeekMetric(index + 1, trend.weeklyNet[index]), params);
        }
        card.addView(weeks);

        return card;
    }

    private LinearLayout accountWeekMetric(int weekNumber, long amount) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setPadding(dp(3), dp(5), dp(3), dp(5));
        metric.setBackground(tileBackground(Color.WHITE, COLOR_BORDER));

        TextView label = text("W" + weekNumber, 10, COLOR_MUTED, false);
        styleBottomNavLabel(label);
        label.setGravity(Gravity.CENTER);
        TextView value = text(signedMoney(amount), 10, amount >= 0 ? COLOR_GREEN : COLOR_RED, true);
        styleBottomNavLabel(value);
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        metric.addView(label);
        metric.addView(value);
        return metric;
    }

    private void renderBorrowLendAnalytics(LinearLayout parent, String title, long startInclusive,
                                           long endExclusive, boolean showTotals) {
        LinearLayout panel = panel();
        addPanelTitle(panel, title);
        long pendingBorrowed = 0;
        long pendingLent = 0;
        long completedBorrowed = 0;
        long completedLent = 0;
        long newBorrowed = 0;
        long newLent = 0;
        long completedThisPeriod = 0;
        int pendingCount = 0;
        int completedCount = 0;

        Cursor cursor = db.getBorrowLendRecords();
        try {
            while (cursor.moveToNext()) {
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                long amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
                boolean completed = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1;
                long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
                int completedAtColumn = cursor.getColumnIndexOrThrow("completed_at");
                long completedAt = cursor.isNull(completedAtColumn) ? 0 : cursor.getLong(completedAtColumn);

                if (ExpenseDbHelper.TYPE_BORROWED.equals(type)) {
                    if (completed) {
                        completedBorrowed += amount;
                    } else {
                        pendingBorrowed += amount;
                    }
                    if (createdAt >= startInclusive && createdAt < endExclusive) {
                        newBorrowed += amount;
                    }
                } else if (ExpenseDbHelper.TYPE_LENDED.equals(type)) {
                    if (completed) {
                        completedLent += amount;
                    } else {
                        pendingLent += amount;
                    }
                    if (createdAt >= startInclusive && createdAt < endExclusive) {
                        newLent += amount;
                    }
                }

                if (completed) {
                    completedCount++;
                    if (completedAt >= startInclusive && completedAt < endExclusive) {
                        completedThisPeriod += amount;
                    }
                } else {
                    pendingCount++;
                }
            }
        } finally {
            cursor.close();
        }

        if (showTotals) {
            addInlineSummaryGrid(panel,
                    new String[]{"Pending Borrowed", "Pending Lent", "Completed Borrowed", "Completed Lent"},
                    new String[]{ExpenseDbHelper.formatMoney(pendingBorrowed), ExpenseDbHelper.formatMoney(pendingLent),
                            ExpenseDbHelper.formatMoney(completedBorrowed), ExpenseDbHelper.formatMoney(completedLent)});
            addSpaceTo(panel, 8);
        }
        panel.addView(compactMetricRow("New borrowed", ExpenseDbHelper.formatMoney(newBorrowed), COLOR_GREEN));
        panel.addView(compactMetricRow("New lent", ExpenseDbHelper.formatMoney(newLent), COLOR_RED));
        panel.addView(compactMetricRow("Completed", ExpenseDbHelper.formatMoney(completedThisPeriod), COLOR_TEAL));
        panel.addView(text("Pending " + pendingCount + " | Completed " + completedCount +
                " | Net open " + signedMoney(pendingBorrowed - pendingLent), 12, COLOR_MUTED, false));
        parent.addView(panel);
    }

    private void addCategoryPieChart(LinearLayout panel, String type, long startInclusive, long endExclusive, long expectedTotal) {
        ArrayList<PieSlice> slices = new ArrayList<>();
        long total = 0;
        Cursor cursor = db.getCategoryBreakdown(type, startInclusive, endExclusive);
        try {
            int index = 0;
            while (cursor.moveToNext()) {
                long amount = cursor.getLong(cursor.getColumnIndexOrThrow("total"));
                if (amount <= 0) {
                    continue;
                }
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                int color = CHART_COLORS[index % CHART_COLORS.length];
                slices.add(new PieSlice(name, amount, color));
                total += amount;
                index++;
            }
        } finally {
            cursor.close();
        }

        if (slices.isEmpty()) {
            return;
        }

        if (expectedTotal > 0 && total <= 0) {
            total = expectedTotal;
        }

        LinearLayout chartRow = horizontalRow();
        chartRow.setGravity(Gravity.CENTER_VERTICAL);
        PieChartView chart = new PieChartView(this, slices, total);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        chartParams.setMargins(0, 0, dp(12), 0);
        chartRow.addView(chart, chartParams);

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        for (PieSlice slice : slices) {
            legend.addView(pieLegendRow(slice, total));
        }
        chartRow.addView(legend, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(chartRow);
        addSpaceTo(panel, 8);
    }

    private LinearLayout pieLegendRow(PieSlice slice, long total) {
        LinearLayout row = horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);

        View swatch = new View(this);
        swatch.setBackground(tileBackground(slice.color, Color.TRANSPARENT));
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        swatchParams.setMargins(0, 0, dp(8), 0);
        row.addView(swatch, swatchParams);

        TextView name = text(slice.label, 11, COLOR_MUTED, false);
        styleLabel(name);
        TextView value = text(percentText(slice.amount, total),
                11, COLOR_NAVY_TEXT, true);
        styleLabel(value);
        value.setGravity(Gravity.RIGHT);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void addInsightCategoryRows(LinearLayout panel, String title, String type, long startInclusive,
                                        long endExclusive, long total, boolean showProgress) {
        if (title != null && !title.isEmpty()) {
            TextView heading = text(title, 15, COLOR_NAVY_TEXT, true);
            styleSectionHeading(heading);
            panel.addView(heading);
        }
        Cursor cursor = db.getCategoryBreakdown(type, startInclusive, endExclusive);
        boolean hasRows = false;
        try {
            while (cursor.moveToNext()) {
                hasRows = true;
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                long amount = cursor.getLong(cursor.getColumnIndexOrThrow("total"));
                int color = ExpenseDbHelper.TYPE_EXPENSE.equals(type) ? COLOR_RED : COLOR_GREEN;
                panel.addView(compactMetricRow(name + " | " + percentText(amount, total),
                        ExpenseDbHelper.formatMoney(amount), color));
                if (showProgress) {
                    panel.addView(progressBar(amount, total, color));
                }
                addSpaceTo(panel, 8);
            }
        } finally {
            cursor.close();
        }
        if (!hasRows) {
            panel.addView(text("No " + type + " categories in this period.", 12, COLOR_MUTED, false));
        }
    }

    private void addAccountActivityRows(LinearLayout panel, long startInclusive, long endExclusive, boolean hideQuietAccounts) {
        Cursor cursor = db.getAccountActivity(startInclusive, endExclusive);
        boolean hasRows = false;
        ArrayList<AccountActivity> rows = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                long income = cursor.getLong(cursor.getColumnIndexOrThrow("income_total"));
                long expense = cursor.getLong(cursor.getColumnIndexOrThrow("expense_total"));
                if (hideQuietAccounts && income == 0 && expense == 0) {
                    continue;
                }
                hasRows = true;
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                long balance = cursor.getLong(cursor.getColumnIndexOrThrow("balance"));
                rows.add(new AccountActivity(name, income, expense, balance));
            }
        } finally {
            cursor.close();
        }
        if (!hasRows) {
            panel.addView(text(hideQuietAccounts ? "No account movement in this period." : "No accounts available.",
                    14, Color.rgb(102, 112, 133), false));
        } else {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setPadding(0, 0, dp(4), 0);
            for (AccountActivity row : rows) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(238), ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, dp(10), 0);
                rowLayout.addView(accountActivityCard(row), params);
            }
            scroll.addView(rowLayout, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            panel.addView(scroll);
        }
    }

    private LinearLayout accountActivityCard(AccountActivity activity) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackground(tileBackground(COLOR_FIELD, COLOR_BORDER));

        LinearLayout top = horizontalRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(activity.name, 14, COLOR_NAVY_TEXT, true);
        styleTransactionName(name);
        TextView balance = text(ExpenseDbHelper.formatMoney(activity.balance), 13,
                activity.balance >= 0 ? COLOR_NAVY_TEXT : COLOR_RED, true);
        styleTransactionAmount(balance);
        balance.setGravity(Gravity.RIGHT);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(balance, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(top);

        addSpaceTo(card, 8);
        LinearLayout metrics = horizontalRow();
        metrics.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
        metrics.addView(accountActivityMetric("Income", activity.income, COLOR_GREEN), weightParams());
        metrics.addView(accountActivityMetric("Expense", activity.expense, COLOR_RED), weightParams());
        long net = activity.income - activity.expense;
        metrics.addView(accountActivityMetric("Net", net, net >= 0 ? COLOR_GREEN : COLOR_RED), weightParams());
        card.addView(metrics);

        return card;
    }

    private LinearLayout accountActivityMetric(String label, long amount, int color) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setPadding(dp(4), dp(4), dp(4), dp(4));
        metric.setBackground(tileBackground(Color.WHITE, COLOR_BORDER));

        TextView labelView = text(label, 10, COLOR_MUTED, false);
        styleBottomNavLabel(labelView);
        labelView.setGravity(Gravity.CENTER);
        TextView valueView = text("Net".equals(label) ? signedMoney(amount) : ExpenseDbHelper.formatMoney(amount),
                11, color, true);
        styleLabel(valueView);
        valueView.setGravity(Gravity.CENTER);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        metric.addView(labelView);
        metric.addView(valueView);
        return metric;
    }

    private void addMonthlyCategoryAnalytics(LinearLayout panel, String type, long currentStart, long currentEnd,
                                             long currentTotal) {
        Cursor current = db.getCategoryBreakdown(type, currentStart, currentEnd);
        boolean hasRows = false;
        try {
            while (current.moveToNext()) {
                hasRows = true;
                String name = current.getString(current.getColumnIndexOrThrow("name"));
                long amount = current.getLong(current.getColumnIndexOrThrow("total"));
                int color = ExpenseDbHelper.TYPE_EXPENSE.equals(type) ? COLOR_RED : COLOR_GREEN;
                panel.addView(compactMetricRow(name + " | " + percentText(amount, currentTotal),
                        ExpenseDbHelper.formatMoney(amount), color));
                panel.addView(progressBar(amount, currentTotal, color));
                addSpaceTo(panel, 10);
            }
        } finally {
            current.close();
        }
        if (!hasRows) {
            panel.addView(text("No " + type + " categories this month.", 14, Color.rgb(102, 112, 133), false));
        }
    }

    private void addTopSpendingRows(LinearLayout panel, long startInclusive, long endExclusive, long totalExpense) {
        Cursor cursor = db.getCategoryBreakdown(ExpenseDbHelper.TYPE_EXPENSE, startInclusive, endExclusive);
        int rank = 1;
        try {
            while (cursor.moveToNext() && rank <= 5) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                long amount = cursor.getLong(cursor.getColumnIndexOrThrow("total"));
                panel.addView(compactMetricRow("#" + rank + " " + name + " | " + percentText(amount, totalExpense),
                        ExpenseDbHelper.formatMoney(amount), COLOR_RED));
                addSpaceTo(panel, 8);
                rank++;
            }
        } finally {
            cursor.close();
        }
        if (rank == 1) {
            panel.addView(text("No expense categories this month.", 14, Color.rgb(102, 112, 133), false));
        }
    }

    private String topCategoryText(String type, long startInclusive, long endExclusive) {
        Cursor cursor = db.getCategoryBreakdown(type, startInclusive, endExclusive);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("name")) + " at " +
                        ExpenseDbHelper.formatMoney(cursor.getLong(cursor.getColumnIndexOrThrow("total")));
            }
        } finally {
            cursor.close();
        }
        return ExpenseDbHelper.TYPE_EXPENSE.equals(type) ? "No expense category" : "No income source";
    }

    private String percentText(long amount, long total) {
        if (total <= 0) {
            return "0%";
        }
        return String.format(Locale.US, "%.1f%%", (amount * 100.0) / total);
    }

    private long todayExpense() {
        long start = startOfDay();
        return db.getTotalForType(ExpenseDbHelper.TYPE_EXPENSE, start, nextDayStart());
    }

    private long startOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long startOfWeek() {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long nextWeekStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startOfWeek());
        calendar.add(Calendar.DAY_OF_YEAR, 7);
        return calendar.getTimeInMillis();
    }

    private long startOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long startOfPreviousMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.MONTH, -1);
        return calendar.getTimeInMillis();
    }

    private long nextMonthStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startOfMonth());
        calendar.add(Calendar.MONTH, 1);
        return calendar.getTimeInMillis();
    }

    private long nextDayStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startOfDay());
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        return calendar.getTimeInMillis();
    }

    private int daysInCurrentMonth() {
        Calendar calendar = Calendar.getInstance();
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private int bucketIndex(long date, long startInclusive, int bucketCount) {
        long dayMs = 24L * 60L * 60L * 1000L;
        int index = (int) ((date - startInclusive) / dayMs);
        return index >= 0 && index < bucketCount ? index : -1;
    }

    private long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total += value;
        }
        return total;
    }

    private AccountTrend findAccountTrend(ArrayList<AccountTrend> trends, String account) {
        for (AccountTrend trend : trends) {
            if (trend.account.equals(account)) {
                return trend;
            }
        }
        AccountTrend trend = new AccountTrend(account);
        trends.add(trend);
        return trend;
    }

    private int monthWeekCount(long startInclusive, long endExclusive) {
        long weekMs = 7L * 24L * 60L * 60L * 1000L;
        long span = Math.max(1, endExclusive - startInclusive);
        return Math.max(1, Math.min(5, (int) ((span + weekMs - 1) / weekMs)));
    }

    private LinearLayout summaryPanel(String title, String[] labels, String[] values, int[] colors) {
        LinearLayout card = panel();
        TextView heading = text(title, 13, COLOR_NAVY_TEXT, true);
        styleTransactionName(heading);
        card.addView(heading);
        addSpaceTo(card, 7);

        LinearLayout row = horizontalRow();
        for (int index = 0; index < labels.length; index++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(2), 0, dp(2), 0);
            TextView label = text(labels[index], 11, COLOR_MUTED, false);
            styleLabel(label);
            TextView value = text(values[index], 14, colors[index], true);
            styleTransactionAmount(value);
            item.addView(label);
            item.addView(value);
            row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        card.addView(row);
        return card;
    }

    private TextView insightLine(String label, long amount) {
        return text(label + ": " + ExpenseDbHelper.formatMoney(amount), 15, COLOR_NAVY_TEXT, true);
    }

    private LinearLayout compactMetricRow(String label, String value, int valueColor) {
        LinearLayout row = horizontalRow();
        TextView left = text(label, 12, COLOR_MUTED, false);
        styleLabel(left);
        TextView right = text(value, 14, valueColor, true);
        styleTransactionAmount(right);
        right.setGravity(Gravity.RIGHT);
        row.addView(left, weightParams());
        row.addView(right, weightParams());
        return row;
    }

    private LinearLayout progressBar(long value, long max, int color) {
        int filledWeight = value <= 0 || max <= 0 ? 0 : Math.max(4, Math.round((value * 100f) / max));
        return new AnimatedProgressBar(
                this,
                Math.min(100, filledWeight),
                color,
                dp(6),
                tileBackground(Color.rgb(234, 236, 240), Color.TRANSPARENT));
    }

    private void addDashboardInsightRow(LinearLayout panel, String label, String value, int valueColor) {
        LinearLayout row = horizontalRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = text(label, 13, COLOR_MUTED, false);
        styleLabel(left);
        TextView right = text(value, 13, valueColor, true);
        styleTransactionAmount(right);
        right.setGravity(Gravity.RIGHT);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(row);
        addSpaceTo(panel, 6);
    }

    private int metricValueColor(String label, String value) {
        String normalized = label.toLowerCase(Locale.US);
        if (normalized.contains("spend") || normalized.contains("spent") || normalized.contains("expense")) {
            return COLOR_RED;
        }
        if (normalized.contains("income")) {
            return COLOR_GREEN;
        }
        if (normalized.contains("net")) {
            return value.startsWith("-") ? COLOR_RED : COLOR_GREEN;
        }
        return COLOR_NAVY_TEXT;
    }

    private String signedMoney(long cents) {
        if (cents > 0) {
            return "+" + ExpenseDbHelper.formatMoney(cents);
        }
        if (cents < 0) {
            return "-" + ExpenseDbHelper.formatMoney(Math.abs(cents));
        }
        return ExpenseDbHelper.formatMoney(0);
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        styleButton(button);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(tileBackground(COLOR_TEAL, Color.TRANSPARENT));
        button.setMinHeight(dp(40));
        button.setPadding(dp(8), dp(7), dp(8), dp(7));
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout moreMenuCard(String title, String subtitle, View.OnClickListener listener) {
        boolean enabled = listener != null;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setMinimumHeight(dp(58));
        card.setBackground(tileBackground(enabled ? Color.WHITE : COLOR_METRIC_TINT, Color.TRANSPARENT));
        applyCardElevation(card);

        TextView titleView = text(title, 14, enabled ? COLOR_NAVY_TEXT : COLOR_MUTED, true);
        styleTransactionName(titleView);
        TextView subtitleView = text(subtitle, 12, enabled ? COLOR_MUTED : Color.rgb(139, 153, 166), false);
        styleLabel(subtitleView);
        subtitleView.setPadding(0, dp(3), 0, 0);
        card.addView(titleView);
        card.addView(subtitleView);
        if (listener != null) {
            card.setOnClickListener(listener);
        } else {
            card.setAlpha(0.65f);
        }
        return card;
    }

    private TextView subPageBack() {
        TextView back = text("< More", 13, COLOR_TEAL, true);
        back.setPadding(0, 0, 0, dp(8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentMorePage = MORE_HOME;
                renderAppShell();
            }
        });
        return back;
    }

    private TextView mainPageBack() {
        TextView back = text("<", 24, COLOR_TEAL, true);
        back.setGravity(Gravity.LEFT);
        back.setPadding(0, 0, 0, dp(2));
        back.setMinHeight(dp(32));
        back.setContentDescription("Back to main page");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderHome();
            }
        });
        return back;
    }

    private TextView appTile(String title, String subtitle, int iconRes, View.OnClickListener listener) {
        TextView tile = text(title + "\n" + subtitle, 15, COLOR_NAVY_TEXT, true);
        tile.setGravity(Gravity.CENTER);
        tile.setMinHeight(dp(136));
        tile.setPadding(dp(10), dp(16), dp(10), dp(14));
        tile.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        tile.setCompoundDrawablePadding(dp(10));
        tile.setBackground(tileBackground(Color.WHITE, COLOR_BORDER));
        tile.setOnClickListener(listener);
        return tile;
    }

    private ImageView imageToolTile(int imageRes, String description, View.OnClickListener listener) {
        ImageView tile = new ImageView(this);
        tile.setImageResource(imageRes);
        tile.setAdjustViewBounds(false);
        tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.setBackground(tileBackground(Color.WHITE, COLOR_BORDER));
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        tile.setMinimumHeight(dp(112));
        tile.setContentDescription(description);
        tile.setOnClickListener(listener);
        return tile;
    }

    private ImageView disabledToolTile(int imageRes) {
        ImageView tile = new ImageView(this);
        tile.setImageResource(imageRes);
        tile.setAdjustViewBounds(false);
        tile.setScaleType(ImageView.ScaleType.CENTER);
        tile.setBackground(tileBackground(Color.WHITE, COLOR_BORDER));
        tile.setPadding(dp(24), dp(24), dp(24), dp(24));
        tile.setMinimumHeight(dp(112));
        tile.setAlpha(0.25f);
        tile.setEnabled(false);
        return tile;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(tileBackground(Color.WHITE, Color.TRANSPARENT));
        panel.setPadding(dp(12), dp(11), dp(12), dp(11));
        panel.setMinimumHeight(dp(40));
        applyCardElevation(panel);
        return panel;
    }

    private TextView sectionTitle(String title) {
        TextView view = text(title, 15, COLOR_NAVY_TEXT, true);
        styleSectionHeading(view);
        view.setPadding(0, 0, 0, dp(6));
        return view;
    }

    private TextView appTitle(String title) {
        TextView view = text(title, 20, COLOR_NAVY_TEXT, true);
        styleAppTitle(view);
        return view;
    }

    private TextView pageTitle(String title) {
        TextView view = text(title, 18, COLOR_NAVY_TEXT, true);
        stylePageTitle(view);
        return view;
    }

    private TextView pageSubtitle(String subtitle) {
        TextView view = text(subtitle, 12, COLOR_MUTED, false);
        styleLabel(view);
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.0f);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private TextView label(String value) {
        TextView label = text(value, 12, COLOR_MUTED, false);
        styleLabel(label);
        label.setPadding(0, dp(7), 0, dp(3));
        return label;
    }

    private LinearLayout transactionDialogForm(String title, String subtitle) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(12), dp(16), dp(2));

        TextView titleView = text(title, 20, COLOR_NAVY_TEXT, true);
        styleAppTitle(titleView);
        titleView.setPadding(0, 0, 0, dp(2));
        form.addView(titleView);

        TextView subtitleView = text(subtitle, 12, COLOR_MUTED, false);
        styleLabel(subtitleView);
        subtitleView.setPadding(0, 0, 0, dp(12));
        form.addView(subtitleView);
        return form;
    }

    private void addDialogField(LinearLayout form, String labelText, View field) {
        TextView fieldLabel = text(labelText, 11, COLOR_MUTED, true);
        styleLabel(fieldLabel);
        fieldLabel.setAllCaps(true);
        fieldLabel.setPadding(0, dp(4), 0, dp(4));
        form.addView(fieldLabel);
        form.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addSpaceTo(form, 7);
    }

    private void styleDialogWindow(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(tileBackground(Color.WHITE, Color.TRANSPARENT));
        }
    }

    private void styleAppTitle(TextView tv) {
        applyTextStyle(tv, "sans-serif", 20, Typeface.BOLD);
    }

    private void stylePageTitle(TextView tv) {
        applyTextStyle(tv, "sans-serif", 18, Typeface.BOLD);
    }

    private void styleSectionHeading(TextView tv) {
        applyTextStyle(tv, "sans-serif-medium", 15, Typeface.NORMAL);
    }

    private void styleTotalBalance(TextView tv) {
        applyTextStyle(tv, "sans-serif", 24, Typeface.BOLD);
    }

    private void styleCardAmount(TextView tv) {
        applyTextStyle(tv, "sans-serif", 17, Typeface.BOLD);
    }

    private void styleTransactionAmount(TextView tv) {
        applyTextStyle(tv, "sans-serif", 14, Typeface.BOLD);
    }

    private void styleTransactionName(TextView tv) {
        applyTextStyle(tv, "sans-serif-medium", 14, Typeface.NORMAL);
    }

    private void styleTransactionMeta(TextView tv) {
        applyTextStyle(tv, "sans-serif", 12, Typeface.NORMAL);
    }

    private void styleLabel(TextView tv) {
        applyTextStyle(tv, "sans-serif", 12, Typeface.NORMAL);
    }

    private void styleButton(TextView tv) {
        applyTextStyle(tv, "sans-serif-medium", 13, Typeface.NORMAL);
    }

    private void styleBottomNavLabel(TextView tv) {
        applyTextStyle(tv, "sans-serif-medium", 10, Typeface.NORMAL);
    }

    private void applyTextStyle(TextView tv, String family, int sp, int style) {
        tv.setTypeface(Typeface.create(family, style));
        tv.setTextSize(sp);
        tv.setIncludeFontPadding(true);
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return row;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams toolGridParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(112), 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(6), dp(14), 0);
        return form;
    }

    private void showDialog(AlertDialog dialog) {
        dialog.show();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) {
            styleButton(positive);
            positive.setTextColor(COLOR_TEAL);
            positive.setAllCaps(false);
        }
        if (negative != null) {
            styleButton(negative);
            negative.setTextColor(COLOR_MUTED);
            negative.setAllCaps(false);
        }
    }

    private EditText editText(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(13);
        input.setTextColor(COLOR_NAVY_TEXT);
        input.setHintTextColor(Color.rgb(139, 153, 166));
        input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        input.setBackground(tileBackground(COLOR_FIELD, COLOR_BORDER));
        input.setPadding(dp(10), 0, dp(10), 0);
        input.setMinHeight(dp(40));
        return input;
    }

    private Spinner spinner(String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        styleSpinner(spinner);
        return spinner;
    }

    private Spinner optionSpinner(List<Option> options) {
        ArrayAdapter<Option> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        styleSpinner(spinner);
        return spinner;
    }

    private void styleSpinner(Spinner spinner) {
        spinner.setBackground(tileBackground(COLOR_FIELD, COLOR_BORDER));
        spinner.setPadding(dp(7), 0, dp(7), 0);
        spinner.setMinimumHeight(dp(40));
    }

    private String required(EditText input, String label) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private String moneyInput(long cents) {
        return String.valueOf(Math.round(cents / 100f));
    }

    private String titleCase(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int index = 0; index < spinner.getCount(); index++) {
            Object item = spinner.getItemAtPosition(index);
            if (item != null && value.equals(item.toString())) {
                spinner.setSelection(index);
                return;
            }
        }
    }

    private void setOptionSelection(Spinner spinner, long id) {
        for (int index = 0; index < spinner.getCount(); index++) {
            Object item = spinner.getItemAtPosition(index);
            if (item instanceof Option && ((Option) item).id == id) {
                spinner.setSelection(index);
                return;
            }
        }
    }

    private void addSpace(int dp) {
        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, dp(compactSpace(dp))));
    }

    private void addSpaceTo(LinearLayout layout, int dp) {
        View spacer = new View(this);
        layout.addView(spacer, new LinearLayout.LayoutParams(1, dp(compactSpace(dp))));
    }

    private int compactSpace(int value) {
        return Math.max(2, Math.round(value * 0.72f));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applyCardElevation(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(dp(1));
        }
    }

    private GradientDrawable tileBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(8));
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private GradientDrawable circleBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void refreshAfterDataChange() {
        ExpenseWidgetProvider.updateAllWidgets(this);
        renderAppShell();
    }

    private static class AccountTrend {
        final String account;
        final long[] weeklyNet = new long[5];
        long totalNet;

        AccountTrend(String account) {
            this.account = account == null ? "Missing account" : account;
        }
    }

    private static class AccountActivity {
        final String name;
        final long income;
        final long expense;
        final long balance;

        AccountActivity(String name, long income, long expense, long balance) {
            this.name = name == null ? "Missing account" : name;
            this.income = income;
            this.expense = expense;
            this.balance = balance;
        }
    }

    private static class PieSlice {
        final String label;
        final long amount;
        final int color;

        PieSlice(String label, long amount, int color) {
            this.label = label;
            this.amount = amount;
            this.color = color;
        }
    }

    private abstract static class AnimatedChartView extends View
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

    private static class PieChartView extends AnimatedChartView {
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
            centerAmountPaint.setColor(COLOR_NAVY);
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
                slicePaint.setColor(index == selectedIndex ? darkenChartColor(slice.color) : slice.color);
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

    private static int darkenChartColor(int color) {
        return Color.rgb(
                Math.round(Color.red(color) * 0.65f),
                Math.round(Color.green(color) * 0.65f),
                Math.round(Color.blue(color) * 0.65f));
    }

    private static long niceAxisMax(long maxValue) {
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

    private static String formatAxisTick(long valueInCents) {
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

    private static String compactAxisValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static void drawYAxis(Canvas canvas, Paint axisPaint, Paint gridPaint,
                                  float left, float right, float top, float chartHeight,
                                  long axisMax, float density) {
        for (int tick = 0; tick <= 4; tick++) {
            long tickValue = axisMax * (4 - tick) / 4;
            float y = top + chartHeight * tick / 4f;
            canvas.drawText(formatAxisTick(tickValue), left - 8f * density, y + 3f * density, axisPaint);
            canvas.drawLine(left, y, right, y, gridPaint);
        }
    }

    private static float chartBarRadius(float barWidth, float density) {
        return Math.min(4f * density, barWidth * 0.22f);
    }

    private static class SpendingTrendChartView extends AnimatedChartView {
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
            barPaint.setColor(COLOR_RED);
            selectedBarPaint.setColor(COLOR_NAVY);
            gridPaint.setColor(COLOR_BORDER);
            gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f, 8f}, 0f));
            textPaint.setColor(COLOR_MUTED);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
            amountPaint.setColor(COLOR_NAVY);
            amountPaint.setTextAlign(Paint.Align.CENTER);
            amountPaint.setTypeface(Typeface.DEFAULT_BOLD);
            amountPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
            axisPaint.setColor(COLOR_MUTED);
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
            long axisMax = niceAxisMax(max);

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

            drawYAxis(canvas, axisPaint, gridPaint, leftAxis, width - rightPadding, top, chartHeight, axisMax, density);

            for (int index = 0; index < expenses.length; index++) {
                float centerX = leftAxis + slot * index + slot / 2f;
                float fullBarHeight = expenses[index] <= 0
                        ? 0
                        : Math.max(3f * density, chartHeight * expenses[index] / axisMax);
                float barHeight = fullBarHeight * animationProgress;
                barBounds.set(centerX - barWidth / 2f, bottom - barHeight, centerX + barWidth / 2f, bottom);
                float radius = chartBarRadius(barWidth, density);
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

    private static class CashflowBarChartView extends AnimatedChartView {
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
            incomePaint.setColor(COLOR_GREEN);
            expensePaint.setColor(COLOR_RED);
            selectedBarPaint.setColor(COLOR_NAVY);
            gridPaint.setColor(COLOR_BORDER);
            gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f, 8f}, 0f));
            textPaint.setColor(COLOR_MUTED);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
            amountPaint.setColor(COLOR_NAVY);
            amountPaint.setTextAlign(Paint.Align.CENTER);
            amountPaint.setTypeface(Typeface.DEFAULT_BOLD);
            amountPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
            axisPaint.setColor(COLOR_MUTED);
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
            long axisMax = niceAxisMax(max);

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

            drawYAxis(canvas, axisPaint, gridPaint, leftAxis, width - rightPadding, top, chartHeight, axisMax, density);

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
            float radius = chartBarRadius(barWidth, density);
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

    private static class WeeklyBarChartView extends AnimatedChartView {
        private final long[] values;
        private final String[] labels;
        private final int accentColor;
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
            this.accentColor = accentColor;
            barPaint.setColor(accentColor);
            selectedBarPaint.setColor(COLOR_NAVY);
            gridPaint.setColor(Color.rgb(224, 228, 236));
            gridPaint.setStrokeWidth(1f);
            gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f, 8f}, 0f));
            labelPaint.setColor(Color.rgb(91, 107, 123));
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 10f);
            amountPaint.setColor(COLOR_NAVY);
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
            long axisMax = niceAxisMax(max);

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
            float radius = chartBarRadius(barWidth, density);

            drawYAxis(canvas, axisPaint, gridPaint, leftAxis, width - rightPadding,
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
            long axisMax = niceAxisMax(max);

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

    private static class Option {
        final long id;
        final String label;

        Option(long id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
