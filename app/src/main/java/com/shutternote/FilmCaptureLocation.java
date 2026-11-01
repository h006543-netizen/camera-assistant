package com.shutternote;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import java.util.ArrayList;
import java.util.List;

/** Main-thread location sampling for one photo while the app remains in the foreground. */
final class FilmCaptureLocation {
    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationListener listener;
    private FilmCaptureLocationSample latest;
    private Consumer<FilmFrameLocation> pending;
    private long photoReadyNanos;
    private final Runnable timeout = () -> finish(null);

    FilmCaptureLocation(Context context) {
        this.context = context.getApplicationContext();
        manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    boolean hasPermission() {
        return hasPermission(context);
    }

    static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @android.annotation.SuppressLint("MissingPermission") // Checked above; revocation is also caught.
    void start() {
        if (listener != null || manager == null || !hasPermission()) return;
        boolean precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        List<String> providers = new ArrayList<>();
        try {
            if (Build.VERSION.SDK_INT >= 31 && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                providers.add(LocationManager.FUSED_PROVIDER);
            } else {
                if (precise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER);
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException | IllegalArgumentException error) { return; }
        if (providers.isEmpty()) return;
        LocationListener subscription = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                if (listener == this) accept(location);
            }
            @Override public void onProviderDisabled(@NonNull String provider) { if (listener == this) latest = null; }
            @Override public void onProviderEnabled(@NonNull String provider) { }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        };
        listener = subscription;
        boolean subscribed = false;
        for (String provider : providers) {
            try {
                // An enabled provider can still fail independently on a particular device.
                manager.requestLocationUpdates(provider, 1000L, 0f, subscription, Looper.getMainLooper());
                subscribed = true;
                accept(manager.getLastKnownLocation(provider));
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        if (!subscribed) stop();
    }

    void capture(long photoReadyNanos, Consumer<FilmFrameLocation> callback) {
        finish(null);
        this.photoReadyNanos = photoReadyNanos;
        long now = SystemClock.elapsedRealtimeNanos();
        long remaining = photoReadyNanos + FilmCaptureLocationSample.WAIT_NANOS - now;
        if (!hasPermission() || listener == null || photoReadyNanos <= 0 || now < photoReadyNanos || remaining <= 0) {
            callback.accept(null); return;
        }
        FilmFrameLocation position = latest == null ? null : latest.forPhoto(photoReadyNanos, now);
        if (position != null) { callback.accept(position); return; }
        pending = callback;
        handler.postDelayed(timeout, Math.max(1, remaining / 1_000_000L));
    }

    private void accept(Location location) {
        if (location == null || !hasPermission() || !FilmFrameLocation.valid(location.getLatitude(), location.getLongitude())) return;
        long now = SystemClock.elapsedRealtimeNanos();
        FilmCaptureLocationSample sample = new FilmCaptureLocationSample(new FilmFrameLocation(
                location.getLatitude(), location.getLongitude(), location.hasAccuracy() ? location.getAccuracy() : -1),
                location.getElapsedRealtimeNanos());
        if (sample.forPhoto(now, now) == null) return;
        if (latest == null || latest.forPhoto(now, now) == null || sample.betterThan(latest)) latest = sample;
        if (pending != null) {
            FilmFrameLocation position = sample.forPhoto(photoReadyNanos, now);
            if (position != null) finish(position);
        }
    }

    private void finish(FilmFrameLocation position) {
        handler.removeCallbacks(timeout);
        Consumer<FilmFrameLocation> callback = pending;
        pending = null;
        if (callback != null) callback.accept(position);
    }

    void stop() {
        LocationListener previous = listener;
        listener = null;
        latest = null;
        if (previous != null && manager != null) {
            try { manager.removeUpdates(previous); } catch (SecurityException ignored) { }
        }
        finish(null);
    }
}
