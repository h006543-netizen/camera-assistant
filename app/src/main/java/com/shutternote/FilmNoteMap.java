package com.shutternote;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.module.http.HttpRequestUtil;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import java.io.File;
import java.util.function.Consumer;
import static org.maplibre.android.style.layers.PropertyFactory.*;

/** One online OSM map, with explicit lifecycle and no automatic location collection. */
@android.annotation.SuppressLint("ViewConstructor") // Created in code with an explicit lifecycle owner.
final class FilmNoteMap extends FrameLayout implements DefaultLifecycleObserver {
    private static final String STYLE = "{\"version\":8,\"sources\":{\"osm\":{\"type\":\"raster\","
            + "\"tiles\":[\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"],\"tileSize\":256,\"maxzoom\":19,"
            + "\"attribution\":\"© OpenStreetMap contributors\"}},\"layers\":["
            + "{\"id\":\"paper\",\"type\":\"background\",\"paint\":{\"background-color\":\"#EAE7DF\"}},"
            + "{\"id\":\"osm\",\"type\":\"raster\",\"source\":\"osm\"}]}";
    private static boolean initialized;
    private final LifecycleOwner owner;
    private final MapView view;
    private final TextView status;
    private final boolean editable;
    private final Consumer<FilmFrameLocation> selected;
    private MapLibreMap map;
    private GeoJsonSource pointSource;
    private FilmFrameLocation position;
    private boolean destroyed, started, resumed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loadTimeout = this::showLoadTimeout;
    private void showLoadTimeout() {
        if (!destroyed && status.getVisibility() == VISIBLE) status.setText(R.string.note_map_error_retry);
    }

    private static synchronized void initialize(Context context) {
        if (initialized) return;
        MapLibre.getInstance(context.getApplicationContext(), null, org.maplibre.android.WellKnownTileServer.MapLibre,
                org.maplibre.android.RenderingEngine.Type.OPENGL);
        // Keep native conditional requests and also cache HTTP responses using server expiry.
        HttpRequestUtil.setOkHttpClient(new OkHttpClient.Builder()
                .cache(new Cache(new File(context.getCacheDir(), "note_map_tiles"), 32L * 1024 * 1024))
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "ShutterNote/1.0 (Android; com.shutternote.app)").build()))
                .addNetworkInterceptor(chain -> {
                    okhttp3.Response response = chain.proceed(chain.request());
                    if ("tile.openstreetmap.org".equals(response.request().url().host())
                            && response.header("Cache-Control") == null && response.header("Expires") == null)
                        return response.newBuilder().header("Cache-Control", "public, max-age=604800").build();
                    return response;
                }).build());
        HttpRequestUtil.setPrintRequestUrlOnFailure(false);
        initialized = true;
    }

    FilmNoteMap(Context context, LifecycleOwner owner, boolean editable, FilmFrameLocation initial,
                Consumer<FilmFrameLocation> selected) {
        super(context);
        this.owner = owner; this.editable = editable; this.position = initial; this.selected = selected;
        initialize(context);
        view = new MapView(context, MapLibreMapOptions.createFromAttributes(context)
                .textureMode(true).attributionEnabled(false).logoEnabled(false));
        addView(view, new LayoutParams(-1, -1));
        view.onCreate(null);
        status = new TextView(context);
        status.setText(R.string.note_map_loading); status.setTextSize(12);
        status.setTextColor(0xFF272623); status.setBackgroundColor(0xEEEAE7DF);
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        addView(status, new LayoutParams(-1, -2, Gravity.TOP));
        TextView attribution = new TextView(context);
        attribution.setText(R.string.note_map_attribution); attribution.setTextSize(10);
        attribution.setTextColor(0xFF272623); attribution.setBackgroundColor(0xEEEAE7DF);
        attribution.setPadding(dp(4), dp(2), dp(4), dp(2));
        attribution.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Keep the visible credit compact while its transparent link target remains easy to tap.
        FrameLayout attributionTarget = new FrameLayout(context);
        attributionTarget.setMinimumWidth(dp(48));
        attributionTarget.setMinimumHeight(dp(48));
        attributionTarget.setContentDescription(context.getString(R.string.note_map_attribution));
        attributionTarget.setFocusable(true);
        attributionTarget.setOnClickListener(v -> openUrl("https://www.openstreetmap.org/copyright"));
        attributionTarget.addView(attribution, new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END));
        addView(attributionTarget, new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END));
        if (!editable) {
            // A transparent accessible target leaves swipes to the note's scroll/pager.
            View hit = new View(context);
            hit.setContentDescription(context.getString(R.string.note_open_google_maps));
            hit.setFocusable(true); hit.setOnClickListener(v -> openGoogleMaps(context, position));
            addView(hit, 1, new LayoutParams(-1, -1));
        }
        status.setOnClickListener(v -> loadStyle());
        view.addOnDidFailLoadingMapListener(message -> {
            if (!destroyed) { status.setText(R.string.note_map_error_retry); status.setVisibility(VISIBLE); }
        });
        view.addOnDidFinishRenderingMapListener(fully -> {
            if (!destroyed && fully && pointSource != null) { status.setVisibility(GONE); handler.removeCallbacks(loadTimeout); }
        });
        view.getMapAsync(ready -> {
            if (destroyed) return;
            map = ready;
            map.setPrefetchZoomDelta(0);
            map.setMaxZoomPreference(19);
            map.getUiSettings().setAllGesturesEnabled(editable);
            map.getUiSettings().setRotateGesturesEnabled(false);
            map.getUiSettings().setTiltGesturesEnabled(false);
            if (editable) map.addOnMapClickListener(point -> {
                FilmFrameLocation next = new FilmFrameLocation(point.getLatitude(), point.getLongitude(), -1);
                showPoint(next, false); selected.accept(next); return true;
            });
            loadStyle();
        });
        owner.getLifecycle().addObserver(this);
    }
    private void loadStyle() {
        if (map == null || destroyed) return;
        status.setVisibility(VISIBLE); status.setText(R.string.note_map_loading);
        handler.removeCallbacks(loadTimeout); handler.postDelayed(loadTimeout, 20000);
        pointSource = null;
        map.setStyle(new Style.Builder().fromJson(STYLE), style -> {
            if (destroyed) return;
            Drawable drawable = AppCompatResources.getDrawable(getContext(), R.drawable.ic_note_location_pin);
            Bitmap pin = Bitmap.createBitmap(dp(28), dp(36), Bitmap.Config.ARGB_8888);
            drawable.setBounds(0, 0, pin.getWidth(), pin.getHeight()); drawable.draw(new Canvas(pin));
            style.addImage("note-pin", pin);
            pointSource = new GeoJsonSource("note-position", FeatureCollection.fromFeatures(new org.maplibre.geojson.Feature[0]));
            style.addSource(pointSource);
            style.addLayer(new SymbolLayer("note-pin", "note-position")
                    .withProperties(iconImage("note-pin"), iconAnchor(org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM), iconAllowOverlap(true)));
            if (position != null) showPoint(position, true);
            else map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(37.5665, 126.9780), 9));
        });
    }
    void showPoint(FilmFrameLocation point, boolean center) {
        position = point;
        if (pointSource == null || destroyed) return;
        // Web Mercator cannot represent the poles; the stored/linked coordinate remains intact.
        double latitude = Math.max(-85.05112878, Math.min(85.05112878, point.latitude));
        pointSource.setGeoJson(Point.fromLngLat(point.longitude, latitude));
        if (center) map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, point.longitude), 16));
    }
    static void openGoogleMaps(Context context, FilmFrameLocation point) {
        if (point == null) return;
        try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(point.googleMapsUrl()))); }
        catch (android.content.ActivityNotFoundException error) { Toast.makeText(context, R.string.note_map_no_app, Toast.LENGTH_LONG).show(); }
    }
    private void openUrl(String url) {
        try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException error) { Toast.makeText(getContext(), R.string.note_map_no_app, Toast.LENGTH_LONG).show(); }
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (editable && getParent() != null) getParent().requestDisallowInterceptTouchEvent(
                event.getActionMasked() != MotionEvent.ACTION_UP && event.getActionMasked() != MotionEvent.ACTION_CANCEL);
        return super.dispatchTouchEvent(event);
    }
    @Override public void onStart(@NonNull LifecycleOwner owner) { if (!destroyed && !started) { view.onStart(); started = true; } }
    @Override public void onResume(@NonNull LifecycleOwner owner) { if (!destroyed && !resumed) { view.onResume(); resumed = true; } }
    @Override public void onPause(@NonNull LifecycleOwner owner) { if (!destroyed && resumed) { view.onPause(); resumed = false; } }
    @Override public void onStop(@NonNull LifecycleOwner owner) { if (!destroyed && started) { view.onStop(); started = false; } }
    @Override public void onDestroy(@NonNull LifecycleOwner owner) { close(); }
    void lowMemory() { if (!destroyed) view.onLowMemory(); }
    void close() {
        if (destroyed) return;
        owner.getLifecycle().removeObserver(this);
        onPause(owner); onStop(owner); destroyed = true; handler.removeCallbacks(loadTimeout); view.onDestroy();
    }
}
