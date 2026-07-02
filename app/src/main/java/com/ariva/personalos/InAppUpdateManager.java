package com.ariva.personalos;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class InAppUpdateManager {
    static final String PREF_PENDING_APK = "pending_apk";
    private static final long MAX_APK_BYTES = 250L * 1024L * 1024L;

    interface DownloadListener {
        void onProgress(int percent);

        void onComplete(File apk);

        void onError(String message);
    }

    interface InstallListener {
        void onCommitted();

        void onError(String message);
    }

    private InAppUpdateManager() {
    }

    static void download(Context context, UpdateChecker.UpdateInfo update, DownloadListener listener) {
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                File apk = downloadAndVerify(appContext, update, percent ->
                        mainHandler.post(() -> listener.onProgress(percent)));
                mainHandler.post(() -> listener.onComplete(apk));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Update download failed." : error.getMessage();
                mainHandler.post(() -> listener.onError(message));
            } finally {
                executor.shutdown();
            }
        });
    }

    static boolean canRequestInstalls(Context context) {
        return Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls();
    }

    static void install(Context context, File apk, InstallListener listener) {
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            PackageInstaller.Session session = null;
            try {
                validateArchive(appContext, apk);
                PackageInstaller installer = appContext.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(appContext.getPackageName());
                params.setSize(apk.length());
                if (Build.VERSION.SDK_INT >= 31) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                }

                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);
                try (InputStream input = new FileInputStream(apk);
                     OutputStream output = session.openWrite("PersonalOS.apk", 0, apk.length())) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    session.fsync(output);
                }

                Intent resultIntent = new Intent(appContext, UpdateInstallReceiver.class)
                        .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        appContext, sessionId, resultIntent, flags);
                IntentSender statusReceiver = pendingIntent.getIntentSender();
                session.commit(statusReceiver);
                session.close();
                session = null;
                mainHandler.post(listener::onCommitted);
            } catch (Exception error) {
                if (session != null) {
                    session.abandon();
                    session.close();
                }
                String message = error.getMessage() == null ? "Unable to start installation." : error.getMessage();
                mainHandler.post(() -> listener.onError(message));
            } finally {
                executor.shutdown();
            }
        });
    }

    private interface ProgressCallback {
        void onProgress(int percent);
    }

    private static File downloadAndVerify(Context context, UpdateChecker.UpdateInfo update,
                                          ProgressCallback callback) throws Exception {
        if (update.apkUrl == null || update.apkUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("This release does not include an APK download.");
        }
        if (update.sha256 == null || !update.sha256.matches("(?i)^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("This release does not include a valid APK checksum.");
        }

        URL url = new URL(update.apkUrl);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("Update downloads must use HTTPS.");
        }

        File updateDir = new File(context.getCacheDir(), "updates");
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            throw new IllegalStateException("Unable to prepare update storage.");
        }
        File destination = new File(updateDir, "PersonalOS-" + update.versionCode + ".apk");
        File temporary = new File(updateDir, destination.getName() + ".download");
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException("Unable to replace the previous download.");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
        connection.setRequestProperty("User-Agent", "PersonalOS-Android");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Download server returned HTTP " + status + ".");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_APK_BYTES) {
                throw new IllegalStateException("The update file is too large.");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            int lastProgress = -1;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_APK_BYTES) {
                        throw new IllegalStateException("The update file is too large.");
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    if (contentLength > 0) {
                        int progress = Math.min(100, (int) ((total * 100L) / contentLength));
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            callback.onProgress(progress);
                        }
                    }
                }
                output.getFD().sync();
            }

            String actualHash = hex(digest.digest());
            if (!actualHash.equalsIgnoreCase(update.sha256)) {
                temporary.delete();
                throw new SecurityException("The downloaded APK checksum does not match the release.");
            }
            if (destination.exists() && !destination.delete()) {
                temporary.delete();
                throw new IllegalStateException("Unable to replace the previous update.");
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                throw new IllegalStateException("Unable to finalize the update download.");
            }
            callback.onProgress(100);
            return destination;
        } finally {
            connection.disconnect();
        }
    }

    private static void validateArchive(Context context, File apk) {
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null || !context.getPackageName().equals(archive.packageName)) {
            throw new SecurityException("The downloaded APK is not a PersonalOS update.");
        }
        long archiveVersion = Build.VERSION.SDK_INT >= 28
                ? archive.getLongVersionCode()
                : archive.versionCode;
        if (archiveVersion <= UpdateChecker.installedVersionCode(context)) {
            throw new IllegalArgumentException("The downloaded APK is not newer than the installed version.");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            value.append(String.format(Locale.US, "%02X", item));
        }
        return value.toString();
    }
}
