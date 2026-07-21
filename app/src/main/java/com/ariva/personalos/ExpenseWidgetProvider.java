package com.ariva.personalos;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ExpenseWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, ExpenseWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(componentName);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        ExpenseDbHelper db = new ExpenseDbHelper(context);
        long dayStart = startOfDay();
        long monthStart = startOfMonth();
        long now = System.currentTimeMillis() + 1;
        long today = db.getTotalForType(ExpenseDbHelper.TYPE_EXPENSE, dayStart, dayStart + dayMillis());
        long month = db.getTotalForType(ExpenseDbHelper.TYPE_EXPENSE, monthStart, now);
        db.close();

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_expenses);
        views.setTextViewText(R.id.widgetTodaySpent, ExpenseDbHelper.formatMoney(today));
        views.setTextViewText(R.id.widgetMonthSpent, ExpenseDbHelper.formatMoney(month));
        String updatedTime = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        views.setTextViewText(R.id.widgetLastUpdated, "Updated " + updatedTime);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(MainActivity.ACTION_ADD_EXPENSE);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                3001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        views.setOnClickPendingIntent(R.id.widgetAddExpense, pendingIntent);

        manager.updateAppWidget(widgetId, views);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private static long startOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long startOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long dayMillis() {
        return 24L * 60L * 60L * 1000L;
    }
}
