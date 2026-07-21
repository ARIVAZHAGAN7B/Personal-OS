package com.ariva.personalos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UsageSyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!UsageTracker.recentlySynced(appContext)) {
                        UsageTracker.syncRecentDays(appContext, 3);
                    }
                } catch (Throwable ignored) {
                } finally {
                    result.finish();
                }
            }
        }).start();
    }
}
