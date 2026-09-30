package com.shutternote;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Shared policy links and ARCore disclosure. No camera session starts from this class. */
final class PrivacyUi {
    private static final String PREFERENCES = "privacy_notices";
    private static final String AR_NOTICE_SEEN = "ar_notice_v1_seen";
    private static final String GOOGLE_PRIVACY = "https://policies.google.com/privacy";

    private PrivacyUi() { }

    static void openPolicy(Context context) {
        String language = policyLanguage(context);
        if (context.getResources().getBoolean(R.bool.privacy_policy_published)) {
            String suffix = "ko".equals(language) ? "/" : "/" + language + "/";
            openUrl(context, context.getString(R.string.privacy_policy_base_url) + suffix);
        } else {
            // The registered Site is not a public policy until contact details and deployment
            // are complete. Keep the current draft readable instead of opening a dead URL.
            showLocalPolicy(context, language);
        }
    }

    static void beforeRangefinder(Context context, Runnable launch) {
        if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(AR_NOTICE_SEEN, false)) {
            launch.run();
        } else {
            showArNotice(context, launch);
        }
    }

    static void showArNotice(Context context, Runnable launch) {
        String html = Html.escapeHtml(context.getString(R.string.ar_privacy_notice))
                + "<br><br><a href=\"" + GOOGLE_PRIVACY + "\">"
                + Html.escapeHtml(context.getString(R.string.google_privacy_policy)) + "</a>";
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.ar_privacy_title)
                .setView(scrollableText(context, html))
                .setNeutralButton(R.string.privacy_policy, (dialog, which) -> openPolicy(context));
        if (launch != null) {
            builder.setPositiveButton(R.string.ar_privacy_continue, (dialog, which) -> {
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                        .putBoolean(AR_NOTICE_SEEN, true).apply();
                launch.run();
            }).setNegativeButton(R.string.cancel, null);
        } else {
            builder.setPositiveButton(R.string.privacy_close, null);
        }
        builder.show();
    }

    private static String policyLanguage(Context context) {
        String language = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        switch (language) {
            case "en": case "fr": case "ja": return language;
            default: return "ko";
        }
    }

    private static void showLocalPolicy(Context context, String language) {
        try (InputStream input = context.getAssets().open("privacy/" + language + ".html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            new AlertDialog.Builder(context)
                    .setTitle(R.string.privacy_policy)
                    .setView(scrollableText(context, html))
                    .setPositiveButton(R.string.privacy_close, null)
                    .show();
        } catch (IOException error) {
            Toast.makeText(context, R.string.privacy_load_error, Toast.LENGTH_LONG).show();
        }
    }

    private static ScrollView scrollableText(Context context, String html) {
        SpannableStringBuilder text = new SpannableStringBuilder(
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY));
        for (URLSpan span : text.getSpans(0, text.length(), URLSpan.class)) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            String url = span.getURL();
            text.removeSpan(span);
            text.setSpan(new ClickableSpan() {
                @Override public void onClick(View view) { openUrl(context, url); }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView view = new TextView(context);
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        view.setPadding(padding, padding / 2, padding, padding / 2);
        view.setTextSize(16);
        view.setText(text);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        ScrollView scroll = new ScrollView(context);
        scroll.addView(view);
        return scroll;
    }

    private static void openUrl(Context context, String url) {
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme()) && !"mailto".equals(uri.getScheme())) return;
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(context, R.string.privacy_no_browser, Toast.LENGTH_LONG).show();
        }
    }
}
