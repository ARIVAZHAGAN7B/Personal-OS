package com.ariva.personalos;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateChecker {
    static final String PREFS = "personal_os_updates";
    static final String PREF_AUTO_CHECK = "automatic_checks";
    static final String PREF_FEED_URL = "feed_url";
    static final String PREF_LAST_CHECK = "last_check";
    static final long AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    interface Callback {
        void onResult(UpdateInfo update, Exception error);
    }

    static final class UpdateInfo {
        final long versionCode;
        final String versionName;
        final String apkUrl;
        final String releasePageUrl;
        final String releaseNotes;

        UpdateInfo(long versionCode, String versionName, String apkUrl,
                   String releasePageUrl, String releaseNotes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.releasePageUrl = releasePageUrl;
            this.releaseNotes = releaseNotes;
        }

        String downloadUrl() {
            return apkUrl == null || apkUrl.trim().isEmpty() ? releasePageUrl : apkUrl;
        }
    }

    private UpdateChecker() {
    }

    static String feedUrl(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_FEED_URL, context.getString(R.string.default_update_feed_url));
    }

    static long installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) {
            return 0;
        }
    }

    static String installedVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "Unknown" : info.versionName;
        } catch (Exception ignored) {
            return "Unknown";
        }
    }

    static void check(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            UpdateInfo result = null;
            Exception failure = null;
            try {
                result = fetch(feedUrl(appContext));
            } catch (Exception error) {
                failure = error;
            }
            UpdateInfo finalResult = result;
            Exception finalFailure = failure;
            mainHandler.post(() -> callback.onResult(finalResult, finalFailure));
            executor.shutdown();
        });
    }

    private static UpdateInfo fetch(String feedUrl) throws Exception {
        URL url = new URL(feedUrl);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("Update source must use HTTPS.");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "PersonalOS-Android");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Update server returned HTTP " + status + ".");
            }

            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (json.length() > 256 * 1024) {
                        throw new IllegalStateException("Update response is too large.");
                    }
                    json.append(line);
                }
            }

            JSONObject object = new JSONObject(json.toString());
            return new UpdateInfo(
                    object.getLong("versionCode"),
                    object.getString("versionName"),
                    object.optString("apkUrl", ""),
                    object.optString("releasePageUrl", ""),
                    object.optString("releaseNotes", ""));
        } finally {
            connection.disconnect();
        }
    }
}
