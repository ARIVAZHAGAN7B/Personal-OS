package com.ariva.personalos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DiaryActivity extends Activity {
    static final String EXTRA_ENTRY_DATE = "entry_date";

    private static final String PAGE_WRITE = "write";
    private static final String PAGE_ENTRIES = "entries";
    private static final String PAGE_HEATMAP = "heatmap";
    private static final String PAGE_DETAIL = "detail";
    private static final String FILTER_ALL = "all";
    private static final String FILTER_WEEK = "week";
    private static final String FILTER_MONTH = "month";
    private static final String FILTER_YEAR = "year";
    private static final String FILTER_CUSTOM = "custom";

    private static final int COLOR_SCREEN = Color.rgb(247, 249, 252);
    private static final int COLOR_NAVY_TEXT = Color.rgb(13, 34, 54);
    private static final int COLOR_MUTED = Color.rgb(91, 107, 123);
    private static final int COLOR_TEAL = Color.rgb(0, 110, 130);
    private static final int COLOR_RED = Color.rgb(180, 35, 24);
    private static final int COLOR_BORDER = Color.rgb(224, 228, 236);
    private static final int COLOR_FIELD = Color.rgb(250, 252, 254);
    private static final int COLOR_HEAT_LOW = Color.rgb(211, 239, 241);
    private static final int COLOR_HEAT_MEDIUM = Color.rgb(108, 190, 196);
    private static final int COLOR_HEAT_STRONG = Color.rgb(45, 166, 171);
    private static final int COLOR_HEAT_HIGH = Color.rgb(0, 110, 130);

    private final Calendar selectedDate = Calendar.getInstance();
    private final SimpleDateFormat fullDateFormat =
            new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat navigationDateFormat =
            new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat shortDateFormat =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat dayNumberFormat =
            new SimpleDateFormat("dd", Locale.getDefault());
    private final SimpleDateFormat monthFormat =
            new SimpleDateFormat("MMM", Locale.getDefault());
    private final SimpleDateFormat monthYearFormat =
            new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat weekdayFormat =
            new SimpleDateFormat("EEEE", Locale.getDefault());
    private final SimpleDateFormat updatedFormat =
            new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault());

    private DiaryDbHelper db;
    private AppUi ui;
    private LinearLayout content;
    private EditText bodyInput;
    private long filterStart;
    private long filterEnd;
    private String activeFilter = FILTER_ALL;
    private boolean newestFirst = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DiaryDbHelper(this);
        ui = new AppUi(this);
        long requestedDate = getIntent().getLongExtra(EXTRA_ENTRY_DATE, 0);
        if (requestedDate > 0) {
            selectedDate.setTimeInMillis(requestedDate);
        }
        normalize(selectedDate);
        render();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    protected String pageType() {
        return PAGE_WRITE;
    }

    private void render() {
        boolean detailPage = PAGE_DETAIL.equals(pageType());
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(COLOR_SCREEN);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_SCREEN);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(20));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (!detailPage) {
            screen.addView(bottomNavigation(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(68)));
        }

        renderPageHeader();

        if (PAGE_ENTRIES.equals(pageType())) {
            renderEntriesPage();
        } else if (PAGE_HEATMAP.equals(pageType())) {
            renderHeatmapPage();
        } else if (PAGE_DETAIL.equals(pageType())) {
            renderEntryDetail();
        } else {
            renderEditor();
        }
        setContentView(screen);
    }

    private void renderPageHeader() {
        if (PAGE_ENTRIES.equals(pageType())) {
            renderEntriesHeader();
            return;
        }
        if (PAGE_HEATMAP.equals(pageType())) {
            renderHeatmapHeader();
            return;
        }
        if (!PAGE_WRITE.equals(pageType())) {
            content.addView(backButton(), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            ui.addSpace(content, 8);
            content.addView(ui.text(pageTitle(), 22, COLOR_NAVY_TEXT, true));
            content.addView(ui.text(pageSubtitle(), 13, COLOR_MUTED, false));
            ui.addSpace(content, 12);
            return;
        }

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(backButton(), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        View headerSpace = new View(this);
        topRow.addView(headerSpace, new LinearLayout.LayoutParams(0, 1, 1));

        ImageView pen = new ImageView(this);
        pen.setImageResource(android.R.drawable.ic_menu_edit);
        pen.setColorFilter(COLOR_TEAL);
        pen.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
        pen.setBackground(roundedBackground(
                Color.rgb(229, 244, 246), Color.TRANSPARENT, 14));
        pen.setContentDescription("Diary");
        topRow.addView(pen, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        content.addView(topRow);
        ui.addSpace(content, 10);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(ui.text("Write diary", 24, COLOR_NAVY_TEXT, true));
        TextView subtitle = ui.text(pageSubtitle(), 13, COLOR_MUTED, false);
        subtitle.setPadding(0, ui.dp(2), 0, 0);
        titleColumn.addView(subtitle);
        hero.addView(titleColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView diaryArt = new ImageView(this);
        diaryArt.setImageResource(R.drawable.ic_diary);
        diaryArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        diaryArt.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
        diaryArt.setBackground(roundedBackground(
                Color.rgb(235, 248, 249), Color.TRANSPARENT, 28));
        hero.addView(diaryArt, new LinearLayout.LayoutParams(ui.dp(72), ui.dp(72)));
        content.addView(hero);
        ui.addSpace(content, 12);
    }

    private void renderHeatmapHeader() {
        content.addView(backButton(), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        ui.addSpace(content, 10);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(ui.text("Diary heatmap", 24, COLOR_NAVY_TEXT, true));
        TextView subtitle = ui.text("Your writing rhythm,\nmonth by month",
                13, COLOR_MUTED, false);
        subtitle.setPadding(0, ui.dp(3), 0, 0);
        titleColumn.addView(subtitle);
        hero.addView(titleColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView diaryArt = new ImageView(this);
        diaryArt.setImageResource(R.drawable.ic_diary);
        diaryArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        diaryArt.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
        diaryArt.setBackground(roundedBackground(
                Color.rgb(235, 248, 249), Color.TRANSPARENT, 30));
        hero.addView(diaryArt, new LinearLayout.LayoutParams(ui.dp(76), ui.dp(76)));
        content.addView(hero);
        ui.addSpace(content, 12);
    }

    private void renderEntriesHeader() {
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(backButton(), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        topRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));

        ImageView filter = new ImageView(this);
        filter.setImageResource(R.drawable.ic_filter_list);
        filter.setColorFilter(COLOR_TEAL);
        filter.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        filter.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 12));
        filter.setElevation(ui.dp(1));
        filter.setContentDescription("Date filters");
        topRow.addView(filter, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        content.addView(topRow);
        ui.addSpace(content, 10);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(ui.text("Diary entries", 24, COLOR_NAVY_TEXT, true));
        TextView subtitle = ui.text(pageSubtitle(), 13, COLOR_MUTED, false);
        subtitle.setPadding(0, ui.dp(2), 0, 0);
        titleColumn.addView(subtitle);
        hero.addView(titleColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView diaryArt = new ImageView(this);
        diaryArt.setImageResource(R.drawable.ic_diary);
        diaryArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        diaryArt.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
        diaryArt.setBackground(roundedBackground(
                Color.rgb(235, 248, 249), Color.TRANSPARENT, 28));
        hero.addView(diaryArt, new LinearLayout.LayoutParams(ui.dp(72), ui.dp(72)));
        content.addView(hero);
        ui.addSpace(content, 12);
    }

    private String pageTitle() {
        if (PAGE_ENTRIES.equals(pageType())) return "Diary entries";
        if (PAGE_HEATMAP.equals(pageType())) return "Diary heatmap";
        if (PAGE_DETAIL.equals(pageType())) return "Diary entry";
        return "Write diary";
    }

    private String pageSubtitle() {
        long count = db.getEntryCount();
        if (PAGE_HEATMAP.equals(pageType())) {
            return "Your writing rhythm, month by month";
        }
        if (PAGE_DETAIL.equals(pageType())) {
            return fullDateFormat.format(selectedDate.getTime());
        }
        return count + (count == 1 ? " saved entry" : " saved entries");
    }

    private int getWordCount(CharSequence text) {
        if (text == null) return 0;
        String trimmed = text.toString().trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
    }

    private void renderEditor() {
        long date = selectedDate.getTimeInMillis();
        boolean hasEntry = db.hasEntry(date);
        LinearLayout panel = ui.panel();
        panel.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
        panel.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 16));
        panel.setElevation(ui.dp(2));

        TextView sectionTitle = ui.text(hasEntry ? "Edit entry" : "New entry",
                16, COLOR_TEAL, true);
        panel.addView(sectionTitle);
        View underline = new View(this);
        underline.setBackgroundColor(COLOR_TEAL);
        LinearLayout.LayoutParams underlineParams =
                new LinearLayout.LayoutParams(ui.dp(36), ui.dp(3));
        underlineParams.setMargins(0, ui.dp(5), 0, ui.dp(10));
        panel.addView(underline, underlineParams);
        panel.addView(dateNavigator());
        ui.addSpace(panel, 12);

        bodyInput = new EditText(this);
        bodyInput.setText(db.getEntry(date));
        bodyInput.setHint("Write about your day...");
        bodyInput.setTextSize(14);
        bodyInput.setTextColor(COLOR_NAVY_TEXT);
        bodyInput.setHintTextColor(Color.rgb(139, 153, 166));
        bodyInput.setGravity(Gravity.TOP | Gravity.LEFT);
        bodyInput.setMinHeight(ui.dp(260));
        bodyInput.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        bodyInput.setBackground(roundedBackground(COLOR_FIELD, COLOR_BORDER, 12));
        panel.addView(bodyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ui.addSpace(panel, 8);

        String initialBody = bodyInput.getText().toString();
        TextView metricsCount = ui.text(
                getWordCount(initialBody) + " words | " + initialBody.length() + " characters",
                11, COLOR_MUTED, false);
        metricsCount.setGravity(Gravity.RIGHT);
        panel.addView(metricsCount);
        bodyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                metricsCount.setText(getWordCount(text) + " words | " + text.length() + " characters");
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        ui.addSpace(panel, 12);

        Button save = ui.actionButton(hasEntry ? "Update entry" : "Save entry", v -> saveEntry());
        save.setTextSize(14);
        save.setMinHeight(ui.dp(48));
        save.setBackground(roundedBackground(COLOR_TEAL, Color.TRANSPARENT, 12));
        panel.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)));
        if (hasEntry) {
            Button delete = ui.actionButton("Delete", v -> confirmDelete(false));
            delete.setTextSize(13);
            delete.setTextColor(COLOR_RED);
            delete.setBackground(roundedBackground(Color.WHITE, COLOR_RED, 12));
            LinearLayout.LayoutParams deleteParams =
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48));
            deleteParams.setMargins(0, ui.dp(8), 0, 0);
            panel.addView(delete, deleteParams);
        }
        content.addView(panel);
    }

    private LinearLayout dateNavigator() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button previous = dateArrowButton("<", "Previous day");
        previous.setOnClickListener(v -> moveSelectedDate(-1));
        row.addView(previous, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));

        Button date = new Button(this);
        date.setText(navigationDateFormat.format(selectedDate.getTime()) +
                "\n" + selectedDateContext());
        date.setTextSize(11);
        date.setTextColor(COLOR_NAVY_TEXT);
        date.setAllCaps(false);
        date.setGravity(Gravity.CENTER);
        date.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_my_calendar, 0, 0, 0);
        date.setCompoundDrawablePadding(ui.dp(4));
        date.setBackground(roundedBackground(COLOR_FIELD, COLOR_BORDER, 12));
        date.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        date.setOnClickListener(v -> showDatePicker(selectedDate.getTimeInMillis(), value -> {
            selectedDate.setTimeInMillis(value);
            normalize(selectedDate);
            render();
        }));
        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(0, ui.dp(48), 1);
        dateParams.setMargins(ui.dp(6), 0, ui.dp(6), 0);
        row.addView(date, dateParams);

        Button next = dateArrowButton(">", "Next day");
        next.setOnClickListener(v -> moveSelectedDate(1));
        row.addView(next, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        return row;
    }

    private Button dateArrowButton(String symbol, String description) {
        Button button = new Button(this);
        button.setText(symbol);
        button.setTextSize(16);
        button.setTextColor(COLOR_TEAL);
        button.setContentDescription(description);
        button.setBackground(roundedBackground(COLOR_FIELD, COLOR_BORDER, 12));
        button.setPadding(0, 0, 0, 0);
        button.setElevation(ui.dp(1));
        return button;
    }

    private void renderEntriesPage() {
        renderEntryFilters();
        ui.addSpace(content, 10);

        LinearLayout panel = ui.panel();
        panel.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        panel.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 16));
        panel.setElevation(ui.dp(2));
        LinearLayout header = ui.horizontalRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.text("All entries", 17, COLOR_NAVY_TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button sort = compactButton(newestFirst ? "Newest first" : "Oldest first", false);
        sort.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            render();
        });
        header.addView(sort);
        panel.addView(header);
        ui.addSpace(panel, 10);

        Cursor cursor = db.getEntries(filterStart, filterEnd, newestFirst);
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(ui.text(
                        activeFilter.equals(FILTER_ALL)
                                ? "No diary entries yet. Start writing today."
                                : "No entries match this date filter.",
                        12, COLOR_MUTED, false));
                if (activeFilter.equals(FILTER_ALL)) {
                    ui.addSpace(panel, 8);
                    Button writeFirst = ui.actionButton("Write first entry", v -> {
                        startActivity(new Intent(DiaryActivity.this, DiaryActivity.class));
                        finish();
                    });
                    panel.addView(writeFirst, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            } else {
                do {
                    long entryDate = cursor.getLong(cursor.getColumnIndexOrThrow("entry_date"));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    long updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
                    panel.addView(entryRow(entryDate, body, updatedAt));
                    if (!cursor.isLast()) {
                        ui.addSpace(panel, 6);
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        content.addView(panel);
    }

    private void renderEntryFilters() {
        LinearLayout panel = ui.panel();
        panel.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        panel.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 16));
        panel.setElevation(ui.dp(2));

        TextView title = ui.text("Filter by date", 15, COLOR_NAVY_TEXT, true);
        title.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_my_calendar, 0, 0, 0);
        title.setCompoundDrawablePadding(ui.dp(9));
        title.setCompoundDrawableTintList(ColorStateList.valueOf(COLOR_TEAL));
        panel.addView(title);
        ui.addSpace(panel, 10);

        LinearLayout quickRow = ui.horizontalRow();
        quickRow.addView(quickFilterButton("All", FILTER_ALL), ui.weightParams());
        quickRow.addView(quickFilterButton("Week", FILTER_WEEK), ui.weightParams());
        quickRow.addView(quickFilterButton("Month", FILTER_MONTH), ui.weightParams());
        quickRow.addView(quickFilterButton("Year", FILTER_YEAR), ui.weightParams());
        panel.addView(quickRow);
        ui.addSpace(panel, 8);

        LinearLayout customRow = ui.horizontalRow();
        Button from = dateRangeButton(
                "From date",
                filterStart > 0 ? shortDateFormat.format(new Date(filterStart)) : "Select date");
        from.setOnClickListener(v -> showDatePicker(
                filterStart > 0 ? filterStart : System.currentTimeMillis(),
                value -> {
                    filterStart = value;
                    if (filterEnd <= filterStart) {
                        filterEnd = filterStart + 24L * 60L * 60L * 1000L;
                    }
                    activeFilter = FILTER_CUSTOM;
                    render();
                }));
        customRow.addView(from, ui.weightParams());

        long displayedEnd = filterEnd > 0
                ? filterEnd - 24L * 60L * 60L * 1000L
                : System.currentTimeMillis();
        Button to = dateRangeButton(
                "To date",
                filterEnd > 0 ? shortDateFormat.format(new Date(displayedEnd)) : "Select date");
        to.setOnClickListener(v -> showDatePicker(displayedEnd, value -> {
            filterEnd = value + 24L * 60L * 60L * 1000L;
            if (filterStart <= 0 || filterStart >= filterEnd) {
                filterStart = value;
            }
            activeFilter = FILTER_CUSTOM;
            render();
        }));
        customRow.addView(to, ui.weightParams());
        panel.addView(customRow);
        content.addView(panel);
    }

    private Button dateRangeButton(String label, String value) {
        Button button = new Button(this);
        button.setText(label + "\n" + value);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTextColor(COLOR_MUTED);
        button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        button.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_my_calendar, 0, 0, 0);
        button.setCompoundDrawablePadding(ui.dp(5));
        button.setBackground(roundedBackground(COLOR_FIELD, COLOR_BORDER, 12));
        button.setMinHeight(ui.dp(50));
        button.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4));
        return button;
    }

    private Button quickFilterButton(String label, String filter) {
        Button button = compactButton(label, filter.equals(activeFilter));
        button.setOnClickListener(v -> {
            applyQuickFilter(filter);
            render();
        });
        return button;
    }

    private Button compactButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTextColor(selected ? Color.WHITE : COLOR_NAVY_TEXT);
        button.setBackground(ui.tileBackground(
                selected ? COLOR_TEAL : COLOR_FIELD,
                selected ? Color.TRANSPARENT : COLOR_BORDER));
        button.setMinHeight(ui.dp(38));
        button.setPadding(ui.dp(5), ui.dp(2), ui.dp(5), ui.dp(2));
        button.setElevation(selected ? ui.dp(1) : 0);
        return button;
    }

    private void applyQuickFilter(String filter) {
        activeFilter = filter;
        if (FILTER_ALL.equals(filter)) {
            filterStart = 0;
            filterEnd = 0;
            return;
        }
        Calendar start = Calendar.getInstance();
        normalize(start);
        if (FILTER_WEEK.equals(filter)) {
            start.setFirstDayOfWeek(Calendar.MONDAY);
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        } else if (FILTER_MONTH.equals(filter)) {
            start.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            start.set(Calendar.DAY_OF_YEAR, 1);
        }
        filterStart = start.getTimeInMillis();
        Calendar end = (Calendar) start.clone();
        if (FILTER_WEEK.equals(filter)) {
            end.add(Calendar.DAY_OF_YEAR, 7);
        } else if (FILTER_MONTH.equals(filter)) {
            end.add(Calendar.MONTH, 1);
        } else {
            end.add(Calendar.YEAR, 1);
        }
        filterEnd = end.getTimeInMillis();
    }

    private LinearLayout entryRow(long entryDate, String body, long updatedAt) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(8), ui.dp(8), ui.dp(8));
        row.setBackground(roundedBackground(COLOR_FIELD, COLOR_BORDER, 14));
        row.setElevation(ui.dp(1));

        View accent = new View(this);
        accent.setBackgroundColor(COLOR_TEAL);
        LinearLayout.LayoutParams accentParams =
                new LinearLayout.LayoutParams(ui.dp(3), ui.dp(70));
        accentParams.setMargins(0, 0, ui.dp(8), 0);
        row.addView(accent, accentParams);

        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundedBackground(
                Color.rgb(235, 248, 249), Color.TRANSPARENT, 12));
        badge.addView(centeredText(dayNumberFormat.format(new Date(entryDate)), 19, COLOR_TEAL, true));
        badge.addView(centeredText(
                monthFormat.format(new Date(entryDate)).toUpperCase(Locale.US),
                9, COLOR_NAVY_TEXT, true));
        badge.addView(centeredText(weekdayFormat.format(new Date(entryDate)),
                8, COLOR_MUTED, false));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ui.dp(58), ui.dp(70));
        badgeParams.setMargins(0, 0, ui.dp(10), 0);
        row.addView(badge, badgeParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView entryTitle = ui.text(entryTitle(body), 14, COLOR_NAVY_TEXT, true);
        entryTitle.setSingleLine(true);
        entryTitle.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(entryTitle);
        TextView bodyPreview = ui.text(entryPreview(body), 11, COLOR_MUTED, false);
        bodyPreview.setMaxLines(2);
        bodyPreview.setEllipsize(TextUtils.TruncateAt.END);
        bodyPreview.setPadding(0, ui.dp(2), 0, ui.dp(2));
        textColumn.addView(bodyPreview);
        TextView updated = ui.text(">  Updated " + updatedFormat.format(new Date(updatedAt)),
                9, COLOR_MUTED, false);
        textColumn.addView(updated);
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = ui.text(">", 22, COLOR_NAVY_TEXT, false);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 18));
        LinearLayout.LayoutParams arrowParams =
                new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34));
        arrowParams.setMargins(ui.dp(6), 0, 0, 0);
        row.addView(arrow, arrowParams);
        row.setOnClickListener(v -> openEntry(entryDate));
        return row;
    }

    private void renderEntryDetail() {
        Cursor cursor = db.getEntryDetails(selectedDate.getTimeInMillis());
        try {
            if (!cursor.moveToFirst()) {
                LinearLayout empty = ui.panel();
                empty.addView(ui.text("There is no diary entry for this date.",
                        14, COLOR_MUTED, false));
                ui.addSpace(empty, 10);
                empty.addView(ui.actionButton("Write an entry", v -> openWriter(
                        selectedDate.getTimeInMillis())));
                content.addView(empty);
                return;
            }
            String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
            long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
            long updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));

            LinearLayout datePanel = ui.panel();
            datePanel.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
            datePanel.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 16));
            datePanel.addView(ui.text(weekdayFormat.format(selectedDate.getTime()),
                    16, COLOR_NAVY_TEXT, true));
            datePanel.addView(ui.text(shortDateFormat.format(selectedDate.getTime()),
                    12, COLOR_MUTED, false));
            content.addView(datePanel);
            ui.addSpace(content, 10);

            LinearLayout bodyPanel = ui.panel();
            bodyPanel.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
            bodyPanel.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 16));
            bodyPanel.addView(ui.sectionTitle("Entry"));
            TextView bodyView = ui.text(body, 14, COLOR_NAVY_TEXT, false);
            bodyView.setTextIsSelectable(true);
            bodyView.setLineSpacing(ui.dp(2), 1.02f);
            bodyPanel.addView(bodyView);
            ui.addSpace(bodyPanel, 10);
            bodyPanel.addView(ui.text(
                    "Created " + updatedFormat.format(new Date(createdAt)),
                    10, COLOR_MUTED, false));
            bodyPanel.addView(ui.text(
                    "Updated " + updatedFormat.format(new Date(updatedAt)),
                    10, COLOR_MUTED, false));
            content.addView(bodyPanel);
            ui.addSpace(content, 10);

            LinearLayout actions = ui.horizontalRow();
            actions.addView(ui.actionButton("Edit entry", v -> openWriter(
                    selectedDate.getTimeInMillis())), ui.weightParams());
            Button delete = ui.actionButton("Delete", v -> confirmDelete(true));
            delete.setBackground(ui.tileBackground(COLOR_RED, Color.TRANSPARENT));
            actions.addView(delete, ui.weightParams());
            content.addView(actions);

            // Word count + read time
            ui.addSpace(content, 10);
            int wordCount = getWordCount(body);
            int readMinutes = Math.max(1, wordCount / 200);
            LinearLayout statsPanel = ui.panel();
            statsPanel.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
            statsPanel.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 12));
            statsPanel.addView(ui.text(
                    wordCount + " words \u00b7 " + readMinutes + " min read",
                    11, COLOR_MUTED, false));
            content.addView(statsPanel);

            // Previous / Next entry navigation
            long prevDate = db.getPreviousEntryDate(selectedDate.getTimeInMillis());
            long nextDate = db.getNextEntryDate(selectedDate.getTimeInMillis());
            if (prevDate > 0 || nextDate > 0) {
                ui.addSpace(content, 10);
                LinearLayout navRow = new LinearLayout(this);
                navRow.setOrientation(LinearLayout.HORIZONTAL);
                navRow.setGravity(Gravity.CENTER_VERTICAL);

                if (prevDate > 0) {
                    final long pd = prevDate;
                    Button prev = ui.actionButton("← Previous", v -> {
                        selectedDate.setTimeInMillis(pd);
                        render();
                    });
                    prev.setTextColor(COLOR_TEAL);
                    prev.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 12));
                    navRow.addView(prev, new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                } else {
                    navRow.addView(new View(this), new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                }

                if (prevDate > 0 && nextDate > 0) {
                    ui.addSpace(navRow, 8);
                }

                if (nextDate > 0) {
                    final long nd = nextDate;
                    Button next = ui.actionButton("Next →", v -> {
                        selectedDate.setTimeInMillis(nd);
                        render();
                    });
                    next.setTextColor(COLOR_TEAL);
                    next.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 12));
                    LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    if (prevDate > 0) {
                        nextParams.setMargins(ui.dp(8), 0, 0, 0);
                    }
                    navRow.addView(next, nextParams);
                } else {
                    navRow.addView(new View(this), new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                }
                content.addView(navRow);
            }
        } finally {
            cursor.close();
        }
    }

    private void renderHeatmapPage() {
        LinearLayout intro = new LinearLayout(this);
        intro.setOrientation(LinearLayout.HORIZONTAL);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(ui.dp(16), ui.dp(15), ui.dp(16), ui.dp(15));
        intro.setBackground(roundedBackground(
                Color.rgb(239, 249, 250), Color.rgb(211, 239, 241), 20));
        intro.setElevation(ui.dp(2));

        TextView sparkle = centeredText("*", 24, COLOR_TEAL, true);
        sparkle.setBackground(roundedBackground(
                Color.rgb(224, 245, 246), Color.TRANSPARENT, 28));
        LinearLayout.LayoutParams sparkleParams =
                new LinearLayout.LayoutParams(ui.dp(54), ui.dp(54));
        sparkleParams.setMargins(0, 0, ui.dp(14), 0);
        intro.addView(sparkle, sparkleParams);
        intro.addView(ui.text(
                "Scroll horizontally to explore months.\nTap a day to read or write.",
                14, COLOR_NAVY_TEXT, false), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(intro);
        ui.addSpace(content, 18);

        LinearLayout legend = ui.horizontalRow();
        legend.setGravity(Gravity.CENTER);
        legend.addView(ui.text("Less", 11, COLOR_MUTED, false));
        legend.addView(legendSwatch(COLOR_FIELD));
        legend.addView(legendSwatch(COLOR_HEAT_LOW));
        legend.addView(legendSwatch(COLOR_HEAT_MEDIUM));
        legend.addView(legendSwatch(COLOR_HEAT_STRONG));
        legend.addView(legendSwatch(COLOR_HEAT_HIGH));
        legend.addView(ui.text("More words", 11, COLOR_MUTED, false));
        content.addView(legend);
        ui.addSpace(content, 18);

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setFillViewport(false);
        LinearLayout months = new LinearLayout(this);
        months.setOrientation(LinearLayout.HORIZONTAL);
        months.setPadding(0, 0, ui.dp(14), 0);
        horizontal.addView(months, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Calendar currentMonth = Calendar.getInstance();
        currentMonth.set(Calendar.DAY_OF_MONTH, 1);
        normalize(currentMonth);
        Calendar startMonth = (Calendar) currentMonth.clone();
        startMonth.add(Calendar.MONTH, -11);
        long firstEntry = db.getFirstEntryDate();
        if (firstEntry > 0) {
            Calendar first = Calendar.getInstance();
            first.setTimeInMillis(firstEntry);
            first.set(Calendar.DAY_OF_MONTH, 1);
            normalize(first);
            if (first.before(startMonth)) {
                startMonth = first;
            }
        }
        Calendar endMonth = (Calendar) currentMonth.clone();
        endMonth.add(Calendar.MONTH, 1);

        int cardWidth = Math.max(ui.dp(286),
                getResources().getDisplayMetrics().widthPixels - ui.dp(42));
        final View[] currentCard = new View[1];
        Calendar cursorMonth = (Calendar) startMonth.clone();
        while (!cursorMonth.after(endMonth)) {
            Calendar month = (Calendar) cursorMonth.clone();
            LinearLayout card = monthHeatmapCard(month, cardWidth, horizontal);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, ui.dp(12), 0);
            months.addView(card, cardParams);
            if (sameMonth(month, currentMonth)) {
                currentCard[0] = card;
            }
            cursorMonth.add(Calendar.MONTH, 1);
        }
        content.addView(horizontal);
        horizontal.post(() -> {
            if (currentCard[0] != null) {
                horizontal.scrollTo(Math.max(0, currentCard[0].getLeft() - ui.dp(8)), 0);
            }
        });
    }

    private LinearLayout monthHeatmapCard(
            Calendar month, int cardWidth, HorizontalScrollView horizontal) {
        long monthStart = month.getTimeInMillis();
        Calendar nextMonth = (Calendar) month.clone();
        nextMonth.add(Calendar.MONTH, 1);
        long monthEnd = nextMonth.getTimeInMillis();
        Map<Long, Integer> activity = new HashMap<>();
        Cursor cursor = db.getMonthActivity(monthStart, monthEnd);
        try {
            while (cursor.moveToNext()) {
                activity.put(
                        cursor.getLong(cursor.getColumnIndexOrThrow("entry_date")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("character_count")));
            }
        } finally {
            cursor.close();
        }

        LinearLayout panel = ui.panel();
        panel.setMinimumWidth(cardWidth);
        panel.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(18));
        panel.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 18));
        panel.setElevation(ui.dp(4));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(ui.text(monthYearFormat.format(month.getTime()),
                21, COLOR_NAVY_TEXT, true));
        titleColumn.addView(ui.text(
                activity.size() + (activity.size() == 1 ? " written day" : " written days"),
                12, COLOR_MUTED, false));
        header.addView(titleColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button previous = dateArrowButton("<", "Previous month");
        previous.setOnClickListener(v -> horizontal.smoothScrollBy(
                -(cardWidth + ui.dp(12)), 0));
        header.addView(previous, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));
        Button next = dateArrowButton(">", "Next month");
        next.setOnClickListener(v -> horizontal.smoothScrollBy(
                cardWidth + ui.dp(12), 0));
        LinearLayout.LayoutParams nextParams =
                new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46));
        nextParams.setMargins(ui.dp(8), 0, 0, 0);
        header.addView(next, nextParams);
        panel.addView(header);
        ui.addSpace(panel, 16);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        String[] weekdays = new String[]{"M", "T", "W", "T", "F", "S", "S"};
        int cellWidth = Math.max(ui.dp(34), (cardWidth - ui.dp(36)) / 7);
        for (String weekday : weekdays) {
            TextView label = centeredText(weekday, 10, COLOR_MUTED, true);
            grid.addView(label, new ViewGroup.LayoutParams(cellWidth, ui.dp(30)));
        }

        Calendar firstDay = (Calendar) month.clone();
        int leading = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        for (int index = 0; index < leading; index++) {
            grid.addView(new View(this), new ViewGroup.LayoutParams(cellWidth, ui.dp(46)));
        }
        int daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= daysInMonth; day++) {
            Calendar date = (Calendar) month.clone();
            date.set(Calendar.DAY_OF_MONTH, day);
            normalize(date);
            long dateStart = date.getTimeInMillis();
            Integer characters = activity.get(dateStart);
            TextView cell = heatmapDayCell(day, dateStart, characters == null ? 0 : characters);
            GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams();
            cellParams.width = Math.max(ui.dp(24), cellWidth - ui.dp(4));
            cellParams.height = ui.dp(42);
            cellParams.setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));
            grid.addView(cell, cellParams);
        }
        int usedCells = leading + daysInMonth;
        for (int index = usedCells; index < 42; index++) {
            grid.addView(new View(this), new ViewGroup.LayoutParams(cellWidth, ui.dp(46)));
        }
        panel.addView(grid);
        return panel;
    }

    private TextView heatmapDayCell(int day, long dateStart, int characters) {
        boolean hasEntry = characters > 0;
        int fill = COLOR_FIELD;
        int textColor = COLOR_MUTED;
        if (characters > 0 && characters < 250) {
            fill = COLOR_HEAT_LOW;
            textColor = COLOR_NAVY_TEXT;
        } else if (characters > 0 && characters < 750) {
            fill = COLOR_HEAT_MEDIUM;
            textColor = COLOR_NAVY_TEXT;
        } else if (characters > 0 && characters < 1500) {
            fill = COLOR_HEAT_STRONG;
            textColor = COLOR_NAVY_TEXT;
        } else if (characters > 0) {
            fill = COLOR_HEAT_HIGH;
            textColor = Color.WHITE;
        }
        boolean today = UsageRange.startOfDay(System.currentTimeMillis()) == dateStart;
        TextView cell = centeredText(String.valueOf(day), 12, textColor, hasEntry);
        cell.setBackground(roundedBackground(
                fill, today ? COLOR_TEAL : Color.TRANSPARENT, 12));
        cell.setContentDescription(shortDateFormat.format(new Date(dateStart)) +
                (hasEntry ? ", diary entry saved" : ", no diary entry"));
        cell.setOnClickListener(v -> {
            if (db.hasEntry(dateStart)) {
                openEntry(dateStart);
            } else {
                openWriter(dateStart);
            }
        });
        return cell;
    }

    private View legendSwatch(int color) {
        View swatch = new View(this);
        swatch.setBackground(roundedBackground(
                color, color == COLOR_FIELD ? COLOR_HEAT_LOW : Color.TRANSPARENT, 6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24));
        params.setMargins(ui.dp(6), 0, 0, 0);
        swatch.setLayoutParams(params);
        return swatch;
    }

    private LinearLayout bottomNavigation() {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(ui.dp(8), ui.dp(5), ui.dp(8), ui.dp(5));
        navigation.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 16));
        navigation.setElevation(ui.dp(6));
        navigation.addView(navItem(
                "Write", android.R.drawable.ic_menu_edit, PAGE_WRITE, DiaryActivity.class), navItemParams());
        navigation.addView(navItem(
                "Entries", R.drawable.ic_nav_transactions, PAGE_ENTRIES, DiaryEntriesActivity.class), navItemParams());
        navigation.addView(navItem(
                "Heatmap", R.drawable.ic_nav_analytics, PAGE_HEATMAP, DiaryHeatmapActivity.class), navItemParams());
        return navigation;
    }

    private TextView navItem(String label, int iconResource, String page,
                             Class<? extends Activity> destination) {
        boolean selected = page.equals(pageType());
        int color = selected ? COLOR_TEAL : COLOR_MUTED;
        TextView item = ui.text(label, 10, color, selected);
        item.setGravity(Gravity.CENTER);
        item.setCompoundDrawablesWithIntrinsicBounds(0, iconResource, 0, 0);
        item.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        item.setCompoundDrawablePadding(ui.dp(3));
        item.setMinHeight(ui.dp(52));
        item.setBackground(roundedBackground(
                selected ? Color.rgb(229, 244, 246) : Color.TRANSPARENT,
                Color.TRANSPARENT, 14));
        item.setContentDescription(label + (selected ? ", selected" : ""));
        item.setEnabled(!selected);
        item.setOnClickListener(v -> {
            startActivity(new Intent(DiaryActivity.this, destination));
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

    private void moveSelectedDate(int amount) {
        selectedDate.add(Calendar.DAY_OF_YEAR, amount);
        normalize(selectedDate);
        render();
    }

    private void saveEntry() {
        String body = bodyInput.getText().toString().trim();
        if (body.isEmpty()) {
            Toast.makeText(this, "Write something before saving.", Toast.LENGTH_SHORT).show();
            return;
        }
        db.saveEntry(selectedDate.getTimeInMillis(), body);
        render();
        // Offer to view the entry after saving
        new android.app.AlertDialog.Builder(this)
                .setTitle("Entry saved")
                .setMessage("Your diary entry has been saved.")
                .setPositiveButton("View entry", (dialog, which) -> {
                    Intent detail = new Intent(this, DiaryEntryDetailActivity.class);
                    detail.putExtra(EXTRA_ENTRY_DATE, selectedDate.getTimeInMillis());
                    startActivity(detail);
                })
                .setNegativeButton("Stay here", null)
                .show();
    }

    private void confirmDelete(boolean leaveDetailPage) {
        new AlertDialog.Builder(this)
                .setTitle("Delete diary entry?")
                .setMessage(fullDateFormat.format(selectedDate.getTime()))
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.deleteEntry(selectedDate.getTimeInMillis());
                    Toast.makeText(this, "Diary entry deleted", Toast.LENGTH_SHORT).show();
                    if (leaveDetailPage) {
                        startActivity(new Intent(this, DiaryEntriesActivity.class));
                        finish();
                    } else {
                        render();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openEntry(long entryDate) {
        Intent detail = new Intent(this, DiaryEntryDetailActivity.class);
        detail.putExtra(EXTRA_ENTRY_DATE, entryDate);
        startActivity(detail);
    }

    private void openWriter(long entryDate) {
        Intent writer = new Intent(this, DiaryActivity.class);
        writer.putExtra(EXTRA_ENTRY_DATE, entryDate);
        startActivity(writer);
    }

    private TextView backButton() {
        TextView back = ui.text("<", 34, COLOR_NAVY_TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(roundedBackground(Color.WHITE, COLOR_BORDER, 14));
        back.setElevation(ui.dp(2));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        return back;
    }

    private String selectedDateContext() {
        long selected = UsageRange.startOfDay(selectedDate.getTimeInMillis());
        long today = UsageRange.startOfDay(System.currentTimeMillis());
        if (selected == today) {
            return "Today";
        }
        if (selected == today - UsageRange.DAY_MS) {
            return "Yesterday";
        }
        if (selected == today + UsageRange.DAY_MS) {
            return "Tomorrow";
        }
        return "Choose date";
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(ui.dp(radiusDp));
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(ui.dp(1), stroke);
        }
        return drawable;
    }

    private TextView centeredText(String value, int size, int color, boolean bold) {
        TextView text = ui.text(value, size, color, bold);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private void showDatePicker(long initialDate, DateSelectionListener listener) {
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(initialDate);
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(Calendar.YEAR, year);
                    selected.set(Calendar.MONTH, month);
                    selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    normalize(selected);
                    listener.onDateSelected(selected.getTimeInMillis());
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private static void normalize(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static boolean sameMonth(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.MONTH) == second.get(Calendar.MONTH);
    }

    private String preview(String body) {
        String normalized = body.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 90 ? normalized : normalized.substring(0, 90) + "...";
    }

    private String entryTitle(String body) {
        String normalized = body == null ? "" : body.trim();
        if (normalized.isEmpty()) {
            return "Untitled entry";
        }
        int lineBreak = normalized.indexOf('\n');
        String firstLine = lineBreak >= 0 ? normalized.substring(0, lineBreak).trim() : normalized;
        if (firstLine.length() <= 36) {
            return firstLine;
        }
        return firstLine.substring(0, 36).trim() + "...";
    }

    private String entryPreview(String body) {
        String normalized = body == null ? "" : body.trim();
        int lineBreak = normalized.indexOf('\n');
        if (lineBreak >= 0 && lineBreak + 1 < normalized.length()) {
            String remaining = normalized.substring(lineBreak + 1).trim();
            if (!remaining.isEmpty()) {
                return preview(remaining);
            }
        }
        return preview(normalized);
    }

    private interface DateSelectionListener {
        void onDateSelected(long dateStart);
    }
}
