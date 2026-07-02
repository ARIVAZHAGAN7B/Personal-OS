package com.ariva.personalos;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class UsageSyncScheduler {
    private UsageSyncScheduler() {
    }

    public static void schedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(context, UsageSyncReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                3001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60L * 60L * 1000L,
                60L * 60L * 1000L,
                pendingIntent);
    }
}
