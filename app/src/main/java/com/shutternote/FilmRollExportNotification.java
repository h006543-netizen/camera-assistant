package com.shutternote;

import android.Manifest;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Called once by the export worker, after the destination stream has closed successfully. */
final class FilmRollExportNotification {
    private static final String CHANNEL = "roll_exports";

    static Intent locationIntent(Context context, Uri document) {
        Intent downloads = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                .setPackage("com.sec.android.app.myfiles");
        PackageManager packages = context.getPackageManager();
        ResolveInfo handler = packages.resolveActivity(downloads, PackageManager.MATCH_DEFAULT_ONLY);
        if (handler == null) {
            downloads.setPackage(null);
            handler = packages.resolveActivity(downloads, PackageManager.MATCH_DEFAULT_ONLY);
        }
        if (handler == null) return fileIntent(document);
        return downloadsIntent(document, new ComponentName(handler.activityInfo.packageName, handler.activityInfo.name));
    }

    static Intent downloadsIntent(Uri document, ComponentName handler) {
        // Resolve first, then use a category for identity. File data/type would break downloads routing.
        return new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).setComponent(handler)
                .addCategory("com.shutternote.export." + document);
    }

    static Intent fileIntent(Uri document) {
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(document, "application/zip")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("ZIP", document));
        return intent;
    }

    static void show(Context context, Uri document) {
        // Providers may not offer persistable grants. That must never turn a saved ZIP into a failure.
        try {
            context.getContentResolver().takePersistableUriPermission(document, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException ignored) { }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL,
                        context.getString(R.string.note_export_channel), NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
            Intent location = locationIntent(context, document);
            boolean downloads = DownloadManager.ACTION_VIEW_DOWNLOADS.equals(location.getAction());
            PendingIntent open = PendingIntent.getActivity(context, 0, location,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent openZip = PendingIntent.getActivity(context, 0, fileIntent(document),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String name = context.getString(R.string.note_export_zip);
            try (Cursor cursor = context.getContentResolver().query(document,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
            } catch (RuntimeException ignored) { }
            NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_note_export)
                    .setContentTitle(context.getString(R.string.note_export_complete))
                    .setContentText(name).setContentIntent(open)
                    .addAction(0, context.getString(downloads
                            ? R.string.note_export_open_location : R.string.note_export_open_zip), open)
                    .setAutoCancel(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
            if (downloads) notification.addAction(0, context.getString(R.string.note_export_open_zip), openZip);
            manager.notify(document.toString(), 0, notification.build());
        } catch (RuntimeException ignored) {
            // Notification settings or provider access can change while export is running.
        }
    }

    private FilmRollExportNotification() { }
}
