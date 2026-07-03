package com.ariva.personalos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
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

        content.addView(backButton());
        content.addView(ui.text(pageTitle(), 20, COLOR_NAVY_TEXT, true));
        content.addView(ui.text(pageSubtitle(), 12, COLOR_MUTED, false));
        ui.addSpace(content, 14);

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

    private void renderEditor() {
        long date = selectedDate.getTimeInMillis();
        boolean hasEntry = db.hasEntry(date);
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle(hasEntry ? "Edit entry" : "New entry"));
        panel.addView(dateNavigator());
        ui.addSpace(panel, 12);

        bodyInput = new EditText(this);
        bodyInput.setText(db.getEntry(date));
        bodyInput.setHint("Write about your day");
        bodyInput.setTextSize(16);
        bodyInput.setTextColor(COLOR_NAVY_TEXT);
        bodyInput.setHintTextColor(Color.rgb(139, 153, 166));
        bodyInput.setGravity(Gravity.TOP | Gravity.LEFT);
        bodyInput.setMinHeight(ui.dp(280));
        bodyInput.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        bodyInput.setBackground(ui.tileBackground(COLOR_FIELD, COLOR_BORDER));
        panel.addView(bodyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ui.addSpace(panel, 6);

        TextView characterCount = ui.text(
                bodyInput.getText().length() + " characters", 11, COLOR_MUTED, false);
        characterCount.setGravity(Gravity.RIGHT);
        panel.addView(characterCount);
        bodyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                characterCount.setText(text.length() + " characters");
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        ui.addSpace(panel, 8);

        LinearLayout actions = ui.horizontalRow();
        actions.addView(ui.actionButton(hasEntry ? "Update entry" : "Save entry", v -> saveEntry()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (hasEntry) {
            Button delete = ui.actionButton("Delete", v -> confirmDelete(false));
            delete.setBackground(ui.tileBackground(COLOR_RED, Color.TRANSPARENT));
            LinearLayout.LayoutParams deleteParams =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            deleteParams.setMargins(ui.dp(8), 0, 0, 0);
            actions.addView(delete, deleteParams);
        }
        panel.addView(actions);
        content.addView(panel);
    }

    private LinearLayout dateNavigator() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button previous = dateArrowButton("<", "Previous day");
        previous.setOnClickListener(v -> moveSelectedDate(-1));
        row.addView(previous, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(48)));

        Button date = new Button(this);
        date.setText(navigationDateFormat.format(selectedDate.getTime()));
        date.setTextSize(13);
        date.setTextColor(COLOR_NAVY_TEXT);
        date.setAllCaps(false);
        date.setSingleLine(true);
        date.setEllipsize(TextUtils.TruncateAt.END);
        date.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_my_calendar, 0, 0, 0);
        date.setCompoundDrawablePadding(ui.dp(6));
        date.setBackground(ui.tileBackground(COLOR_FIELD, COLOR_BORDER));
        date.setOnClickListener(v -> showDatePicker(selectedDate.getTimeInMillis(), value -> {
            selectedDate.setTimeInMillis(value);
            normalize(selectedDate);
            render();
        }));
        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(0, ui.dp(48), 1);
        dateParams.setMargins(ui.dp(8), 0, ui.dp(8), 0);
        row.addView(date, dateParams);

        Button next = dateArrowButton(">", "Next day");
        next.setOnClickListener(v -> moveSelectedDate(1));
        row.addView(next, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(48)));
        return row;
    }

    private Button dateArrowButton(String symbol, String description) {
        Button button = new Button(this);
        button.setText(symbol);
        button.setTextSize(18);
        button.setTextColor(COLOR_TEAL);
        button.setContentDescription(description);
        button.setBackground(ui.tileBackground(COLOR_FIELD, COLOR_BORDER));
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void renderEntriesPage() {
        renderEntryFilters();
        ui.addSpace(content, 14);

        LinearLayout panel = ui.panel();
        LinearLayout header = ui.horizontalRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.sectionTitle("All entries");
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button sort = compactButton(newestFirst ? "Newest first" : "Oldest first", false);
        sort.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            render();
        });
        header.addView(sort);
        panel.addView(header);

        Cursor cursor = db.getEntries(filterStart, filterEnd, newestFirst);
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(ui.text(
                        activeFilter.equals(FILTER_ALL)
                                ? "No diary entries yet."
                                : "No entries match this date filter.",
                        13, COLOR_MUTED, false));
            } else {
                do {
                    long entryDate = cursor.getLong(cursor.getColumnIndexOrThrow("entry_date"));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    long updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
                    panel.addView(entryRow(entryDate, body, updatedAt));
                    if (!cursor.isLast()) {
                        ui.addSpace(panel, 8);
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
        panel.addView(ui.sectionTitle("Filter by date"));

        LinearLayout quickRow = ui.horizontalRow();
        quickRow.addView(quickFilterButton("All", FILTER_ALL), ui.weightParams());
        quickRow.addView(quickFilterButton("Week", FILTER_WEEK), ui.weightParams());
        quickRow.addView(quickFilterButton("Month", FILTER_MONTH), ui.weightParams());
        quickRow.addView(quickFilterButton("Year", FILTER_YEAR), ui.weightParams());
        panel.addView(quickRow);
        ui.addSpace(panel, 10);

        LinearLayout customRow = ui.horizontalRow();
        Button from = compactButton(
                filterStart > 0 ? "From " + shortDateFormat.format(new Date(filterStart)) : "From date",
                FILTER_CUSTOM.equals(activeFilter));
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
        Button to = compactButton(
                filterEnd > 0 ? "To " + shortDateFormat.format(new Date(displayedEnd)) : "To date",
                FILTER_CUSTOM.equals(activeFilter));
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
        button.setTextSize(12);
        button.setTextColor(selected ? Color.WHITE : COLOR_NAVY_TEXT);
        button.setBackground(ui.tileBackground(
                selected ? COLOR_TEAL : COLOR_FIELD,
                selected ? Color.TRANSPARENT : COLOR_BORDER));
        button.setMinHeight(ui.dp(42));
        button.setPadding(ui.dp(7), ui.dp(4), ui.dp(7), ui.dp(4));
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
        row.setPadding(ui.dp(10), ui.dp(9), ui.dp(10), ui.dp(9));
        row.setBackground(ui.tileBackground(COLOR_FIELD, COLOR_BORDER));

        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(ui.tileBackground(Color.WHITE, COLOR_BORDER));
        badge.addView(centeredText(dayNumberFormat.format(new Date(entryDate)), 16, COLOR_TEAL, true));
        badge.addView(centeredText(
                monthFormat.format(new Date(entryDate)).toUpperCase(Locale.US),
                10, COLOR_MUTED, true));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ui.dp(50), ui.dp(52));
        badgeParams.setMargins(0, 0, ui.dp(10), 0);
        row.addView(badge, badgeParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(ui.text(weekdayFormat.format(new Date(entryDate)), 14, COLOR_NAVY_TEXT, true));
        TextView bodyPreview = ui.text(preview(body), 12, COLOR_MUTED, false);
        bodyPreview.setSingleLine(true);
        bodyPreview.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(bodyPreview);
        textColumn.addView(ui.text("Updated " + updatedFormat.format(new Date(updatedAt)),
                10, COLOR_MUTED, false));
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = ui.text(">", 18, COLOR_TEAL, true);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(36)));
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
            datePanel.addView(ui.text(weekdayFormat.format(selectedDate.getTime()),
                    18, COLOR_NAVY_TEXT, true));
            datePanel.addView(ui.text(shortDateFormat.format(selectedDate.getTime()),
                    13, COLOR_MUTED, false));
            content.addView(datePanel);
            ui.addSpace(content, 14);

            LinearLayout bodyPanel = ui.panel();
            bodyPanel.addView(ui.sectionTitle("Entry"));
            TextView bodyView = ui.text(body, 16, COLOR_NAVY_TEXT, false);
            bodyView.setTextIsSelectable(true);
            bodyView.setLineSpacing(ui.dp(3), 1.05f);
            bodyPanel.addView(bodyView);
            ui.addSpace(bodyPanel, 14);
            bodyPanel.addView(ui.text(
                    "Created " + updatedFormat.format(new Date(createdAt)),
                    11, COLOR_MUTED, false));
            bodyPanel.addView(ui.text(
                    "Updated " + updatedFormat.format(new Date(updatedAt)),
                    11, COLOR_MUTED, false));
            content.addView(bodyPanel);
            ui.addSpace(content, 14);

            LinearLayout actions = ui.horizontalRow();
            actions.addView(ui.actionButton("Edit entry", v -> openWriter(
                    selectedDate.getTimeInMillis())), ui.weightParams());
            Button delete = ui.actionButton("Delete", v -> confirmDelete(true));
            delete.setBackground(ui.tileBackground(COLOR_RED, Color.TRANSPARENT));
            actions.addView(delete, ui.weightParams());
            content.addView(actions);
        } finally {
            cursor.close();
        }
    }

    private void renderHeatmapPage() {
        LinearLayout intro = ui.panel();
        intro.addView(ui.text("Scroll horizontally to explore months. Tap a day to read or write.",
                13, COLOR_MUTED, false));
        ui.addSpace(intro, 10);
        LinearLayout legend = ui.horizontalRow();
        legend.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        legend.addView(ui.text("Less", 11, COLOR_MUTED, false));
        legend.addView(legendSwatch(COLOR_HEAT_LOW));
        legend.addView(legendSwatch(COLOR_HEAT_MEDIUM));
        legend.addView(legendSwatch(COLOR_HEAT_HIGH));
        legend.addView(ui.text("More writing", 11, COLOR_MUTED, false));
        intro.addView(legend);
        content.addView(intro);
        ui.addSpace(content, 14);

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
            LinearLayout card = monthHeatmapCard(month, cardWidth);
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

    private LinearLayout monthHeatmapCard(Calendar month, int cardWidth) {
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
        panel.addView(ui.text(monthYearFormat.format(month.getTime()),
                17, COLOR_NAVY_TEXT, true));
        panel.addView(ui.text(
                activity.size() + (activity.size() == 1 ? " written day" : " written days"),
                12, COLOR_MUTED, false));
        ui.addSpace(panel, 10);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        String[] weekdays = new String[]{"M", "T", "W", "T", "F", "S", "S"};
        int cellWidth = Math.max(ui.dp(34), (cardWidth - ui.dp(28)) / 7);
        for (String weekday : weekdays) {
            TextView label = centeredText(weekday, 10, COLOR_MUTED, true);
            grid.addView(label, new ViewGroup.LayoutParams(cellWidth, ui.dp(26)));
        }

        Calendar firstDay = (Calendar) month.clone();
        int leading = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        for (int index = 0; index < leading; index++) {
            grid.addView(new View(this), new ViewGroup.LayoutParams(cellWidth, ui.dp(40)));
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
            cellParams.height = ui.dp(36);
            cellParams.setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));
            grid.addView(cell, cellParams);
        }
        int usedCells = leading + daysInMonth;
        for (int index = usedCells; index < 42; index++) {
            grid.addView(new View(this), new ViewGroup.LayoutParams(cellWidth, ui.dp(40)));
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
        } else if (characters < 750 && characters > 0) {
            fill = COLOR_HEAT_MEDIUM;
            textColor = COLOR_NAVY_TEXT;
        } else if (characters > 0) {
            fill = COLOR_HEAT_HIGH;
            textColor = Color.WHITE;
        }
        boolean today = UsageRange.startOfDay(System.currentTimeMillis()) == dateStart;
        TextView cell = centeredText(String.valueOf(day), 12, textColor, hasEntry);
        cell.setBackground(ui.tileBackground(fill, today ? COLOR_TEAL : Color.TRANSPARENT));
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
        swatch.setBackground(ui.tileBackground(color, Color.TRANSPARENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(13), ui.dp(13));
        params.setMargins(ui.dp(5), 0, 0, 0);
        swatch.setLayoutParams(params);
        return swatch;
    }

    private LinearLayout bottomNavigation() {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(ui.dp(6), ui.dp(4), ui.dp(6), ui.dp(4));
        navigation.setBackground(ui.tileBackground(Color.WHITE, COLOR_BORDER));
        navigation.setElevation(ui.dp(8));
        navigation.addView(navItem(
                "Write", R.drawable.ic_nav_add, PAGE_WRITE, DiaryActivity.class), navItemParams());
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
        TextView item = ui.text(label, 11, color, selected);
        item.setGravity(Gravity.CENTER);
        item.setCompoundDrawablesWithIntrinsicBounds(0, iconResource, 0, 0);
        item.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        item.setCompoundDrawablePadding(ui.dp(3));
        item.setBackground(ui.tileBackground(
                selected ? Color.rgb(229, 244, 246) : Color.TRANSPARENT,
                Color.TRANSPARENT));
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
        Toast.makeText(this, "Diary entry saved", Toast.LENGTH_SHORT).show();
        render();
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
        TextView back = ui.text("<", 24, COLOR_TEAL, true);
        back.setGravity(Gravity.LEFT);
        back.setMinHeight(ui.dp(32));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        return back;
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

    private interface DateSelectionListener {
        void onDateSelected(long dateStart);
    }
}
