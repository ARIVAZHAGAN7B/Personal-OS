package com.ariva.personalos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DiaryActivity extends Activity {
    private static final int COLOR_NAVY_TEXT = Color.rgb(13, 34, 54);
    private static final int COLOR_MUTED = Color.rgb(91, 107, 123);
    private static final int COLOR_TEAL = Color.rgb(0, 110, 130);
    private static final int COLOR_RED = Color.rgb(180, 35, 24);
    private static final int COLOR_BORDER = Color.rgb(224, 228, 236);
    private static final int COLOR_FIELD = Color.rgb(250, 252, 254);

    private final Calendar selectedDate = Calendar.getInstance();
    private final SimpleDateFormat fullDateFormat =
            new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat navigationDateFormat =
            new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat dayNumberFormat =
            new SimpleDateFormat("dd", Locale.getDefault());
    private final SimpleDateFormat monthFormat =
            new SimpleDateFormat("MMM", Locale.getDefault());
    private final SimpleDateFormat weekdayFormat =
            new SimpleDateFormat("EEEE", Locale.getDefault());

    private DiaryDbHelper db;
    private AppUi ui;
    private LinearLayout content;
    private EditText bodyInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DiaryDbHelper(this);
        ui = new AppUi(this);
        normalizeSelectedDate();
        render();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private void render() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 249, 252));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(backButton());
        content.addView(ui.text("Diary", 20, COLOR_NAVY_TEXT, true));
        long entryCount = db.getEntryCount();
        content.addView(ui.text(entryCount + (entryCount == 1 ? " saved entry" : " saved entries"),
                12, COLOR_MUTED, false));
        ui.addSpace(content, 14);

        renderEditor();
        ui.addSpace(content, 16);
        renderHistory();
        setContentView(scrollView);
    }

    private void renderEditor() {
        long date = selectedDate.getTimeInMillis();
        boolean hasEntry = db.hasEntry(date);
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Daily Entry"));
        panel.addView(dateNavigator());
        ui.addSpace(panel, 12);

        bodyInput = new EditText(this);
        bodyInput.setText(db.getEntry(date));
        bodyInput.setHint("Write about your day");
        bodyInput.setTextSize(16);
        bodyInput.setTextColor(COLOR_NAVY_TEXT);
        bodyInput.setHintTextColor(Color.rgb(139, 153, 166));
        bodyInput.setGravity(Gravity.TOP | Gravity.LEFT);
        bodyInput.setMinHeight(ui.dp(220));
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
        actions.addView(ui.actionButton(hasEntry ? "Update" : "Save", v -> saveEntry()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (hasEntry) {
            Button delete = ui.actionButton("Delete", v -> confirmDelete());
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
        date.setOnClickListener(v -> showDatePicker());
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

    private void moveSelectedDate(int amount) {
        selectedDate.add(Calendar.DAY_OF_YEAR, amount);
        normalizeSelectedDate();
        render();
    }

    private void renderHistory() {
        LinearLayout panel = ui.panel();
        panel.addView(ui.sectionTitle("Recent Entries"));
        Cursor cursor = db.getEntries();
        try {
            if (!cursor.moveToFirst()) {
                panel.addView(ui.text("No diary entries yet.", 13, COLOR_MUTED, false));
            } else {
                do {
                    long entryDate = cursor.getLong(cursor.getColumnIndexOrThrow("entry_date"));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    panel.addView(historyRow(entryDate, body));
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

    private LinearLayout historyRow(long entryDate, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(10), ui.dp(9), ui.dp(10), ui.dp(9));
        boolean selected = entryDate == selectedDate.getTimeInMillis();
        row.setBackground(ui.tileBackground(
                selected ? Color.rgb(234, 246, 247) : COLOR_FIELD,
                selected ? COLOR_TEAL : COLOR_BORDER));

        LinearLayout dateBadge = new LinearLayout(this);
        dateBadge.setOrientation(LinearLayout.VERTICAL);
        dateBadge.setGravity(Gravity.CENTER);
        dateBadge.setBackground(ui.tileBackground(Color.WHITE, COLOR_BORDER));
        dateBadge.addView(centeredText(dayNumberFormat.format(new Date(entryDate)), 16, COLOR_TEAL, true));
        dateBadge.addView(centeredText(monthFormat.format(new Date(entryDate)).toUpperCase(Locale.US),
                10, COLOR_MUTED, true));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ui.dp(50), ui.dp(52));
        badgeParams.setMargins(0, 0, ui.dp(10), 0);
        row.addView(dateBadge, badgeParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(ui.text(weekdayFormat.format(new Date(entryDate)), 14, COLOR_NAVY_TEXT, true));
        TextView preview = ui.text(preview(body), 12, COLOR_MUTED, false);
        preview.setSingleLine(true);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(preview);
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = ui.text(">", 18, COLOR_TEAL, true);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(36)));
        row.setOnClickListener(v -> {
            selectedDate.setTimeInMillis(entryDate);
            normalizeSelectedDate();
            render();
        });
        return row;
    }

    private TextView centeredText(String value, int size, int color, boolean bold) {
        TextView text = ui.text(value, size, color, bold);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    normalizeSelectedDate();
                    render();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH));
        dialog.show();
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

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete diary entry?")
                .setMessage(fullDateFormat.format(selectedDate.getTime()))
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.deleteEntry(selectedDate.getTimeInMillis());
                    Toast.makeText(this, "Diary entry deleted", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView backButton() {
        TextView back = ui.text("<", 24, COLOR_TEAL, true);
        back.setGravity(Gravity.LEFT);
        back.setMinHeight(ui.dp(32));
        back.setContentDescription("Back to main page");
        back.setOnClickListener(v -> finish());
        return back;
    }

    private void normalizeSelectedDate() {
        selectedDate.set(Calendar.HOUR_OF_DAY, 0);
        selectedDate.set(Calendar.MINUTE, 0);
        selectedDate.set(Calendar.SECOND, 0);
        selectedDate.set(Calendar.MILLISECOND, 0);
    }

    private String preview(String body) {
        String normalized = body.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 90 ? normalized : normalized.substring(0, 90) + "...";
    }
}
