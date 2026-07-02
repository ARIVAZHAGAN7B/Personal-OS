package com.ariva.personalos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
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
    private final SimpleDateFormat shortDateFormat =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

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
        content.addView(ui.text(shortDateFormat.format(new Date()), 12, COLOR_MUTED, false));
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
        panel.addView(ui.sectionTitle(fullDateFormat.format(selectedDate.getTime())));

        Button chooseDate = ui.actionButton("Choose date", v -> showDatePicker());
        chooseDate.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_my_calendar, 0, 0, 0);
        chooseDate.setCompoundDrawablePadding(ui.dp(7));
        panel.addView(chooseDate);
        ui.addSpace(panel, 10);

        bodyInput = new EditText(this);
        bodyInput.setText(db.getEntry(date));
        bodyInput.setHint("Write about your day");
        bodyInput.setTextSize(15);
        bodyInput.setTextColor(COLOR_NAVY_TEXT);
        bodyInput.setHintTextColor(Color.rgb(139, 153, 166));
        bodyInput.setGravity(Gravity.TOP | Gravity.LEFT);
        bodyInput.setMinHeight(ui.dp(180));
        bodyInput.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        bodyInput.setBackground(ui.tileBackground(COLOR_FIELD, COLOR_BORDER));
        panel.addView(bodyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ui.addSpace(panel, 10);

        panel.addView(ui.actionButton(hasEntry ? "Update entry" : "Save entry", v -> saveEntry()));
        if (hasEntry) {
            ui.addSpace(panel, 8);
            Button delete = ui.actionButton("Delete entry", v -> confirmDelete());
            delete.setBackground(ui.tileBackground(COLOR_RED, Color.TRANSPARENT));
            panel.addView(delete);
        }
        content.addView(panel);
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
        row.setBackground(ui.tileBackground(COLOR_FIELD, COLOR_BORDER));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(ui.text(fullDateFormat.format(new Date(entryDate)), 14, COLOR_NAVY_TEXT, true));
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
