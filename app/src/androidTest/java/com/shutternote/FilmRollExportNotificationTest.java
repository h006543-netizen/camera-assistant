package com.shutternote;

import android.app.PendingIntent;
import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmRollExportNotificationTest {
    @Test public void downloadsLinkDoesNotInvokeAFilePickerOrSetAFileMimeFilter() {
        Uri document = Uri.parse("content://example.documents/document/opaque%3A123");
        ComponentName handler = new ComponentName("example.files", "example.files.Downloads");
        Intent intent = FilmRollExportNotification.downloadsIntent(document, handler);
        assertEquals(DownloadManager.ACTION_VIEW_DOWNLOADS, intent.getAction());
        assertEquals(handler, intent.getComponent());
        assertNull(intent.getData());
        assertNull(intent.getType());
        assertNull(intent.getClipData());
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
    }

    @Test public void zipLinkKeepsTheActualProviderUriAndOnlyReadAccess() {
        Uri document = Uri.parse("content://example.documents/document/opaque%3A123");
        Intent intent = FilmRollExportNotification.fileIntent(document);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("application/zip", intent.getType());
        assertEquals(document, intent.getData());
        assertEquals(document, intent.getClipData().getItemAt(0).getUri());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertEquals(0, intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Test public void laterExportDoesNotReplaceAnEarlierNotificationsTarget() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String base = "content://example.documents/document/" + java.util.UUID.randomUUID();
        ComponentName handler = new ComponentName(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent first = PendingIntent.getActivity(context, 0,
                FilmRollExportNotification.downloadsIntent(Uri.parse(base + "-first"), handler), flags);
        PendingIntent second = PendingIntent.getActivity(context, 0,
                FilmRollExportNotification.downloadsIntent(Uri.parse(base + "-second"), handler), flags);
        try { assertNotEquals(first, second); }
        finally { first.cancel(); second.cancel(); }
    }
}
