package com.example.cameraoption;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 전체화면 카메라 UI가 상태바, 노치, 제스처 영역 및 3버튼 내비게이션바와
 * 겹치지 않도록 시스템 안전 여백을 적용한다.
 */
public final class SystemBarInsets {

    private SystemBarInsets() {
    }

    public static void apply(
            Activity activity,
            View topControls,
            View bottomControls
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        applyDarkNavigationBar(activity);
        applyTopPadding(topControls);
        applyBottomPadding(bottomControls);
    }

    public static void applyWithSeparateStatusBar(
            Activity activity,
            View statusBarSpace,
            View topControls,
            View bottomControls
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().setStatusBarColor(Color.rgb(21, 21, 21));
        WindowCompat.getInsetsController(activity.getWindow(),
                activity.getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        applyDarkNavigationBar(activity);
        ViewCompat.setOnApplyWindowInsetsListener(statusBarSpace, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            setHeight(view, safe.top);
            return insets;
        });
        ViewCompat.requestApplyInsets(statusBarSpace);
        applyTopMargin(topControls);
        applyBottomPadding(bottomControls);
    }

    public static void applyTopAndBottomMargin(
            Activity activity,
            View topControls,
            View bottomControls
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        applyDarkNavigationBar(activity);
        applyTopPadding(topControls);
        applyBottomMargin(bottomControls);
    }

    public static void applyMainScreen(
            Activity activity,
            View statusBarSpace,
            View navigationBarSpace
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().setStatusBarColor(Color.rgb(21, 21, 21));
        WindowCompat.getInsetsController(activity.getWindow(),
                activity.getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        configureDarkNavigationBar(activity);

        ViewCompat.setOnApplyWindowInsetsListener(statusBarSpace, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            setHeight(view, safe.top);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(navigationBarSpace, (view, insets) -> {
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()
                    | WindowInsetsCompat.Type.systemGestures());
            setHeight(view, nav.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(statusBarSpace);
        ViewCompat.requestApplyInsets(navigationBarSpace);
    }

    private static void setHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            view.setLayoutParams(params);
        }
    }

    private static void applyDarkNavigationBar(Activity activity) {
        configureDarkNavigationBar(activity);

        // Android 15+ can draw the app behind the system bar. Cover only that inset,
        // leaving the camera preview and its transparent controls untouched.
        FrameLayout content = activity.findViewById(android.R.id.content);
        View blackInset = new View(activity);
        blackInset.setBackgroundColor(Color.BLACK);
        blackInset.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(blackInset, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));
        ViewCompat.setOnApplyWindowInsetsListener(blackInset, (view, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            setHeight(view, bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(blackInset);
    }

    private static void configureDarkNavigationBar(Activity activity) {
        activity.getWindow().setNavigationBarColor(Color.BLACK);
        WindowCompat.getInsetsController(activity.getWindow(),
                activity.getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    private static void applyTopPadding(View topControls) {
        int initialTopPadding = topControls.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(
                topControls,
                (view, windowInsets) -> {
                    Insets safeInsets = windowInsets.getInsets(
                            WindowInsetsCompat.Type.statusBars()
                                    | WindowInsetsCompat.Type.displayCutout()
                    );
                    view.setPadding(
                            view.getPaddingLeft(),
                            initialTopPadding + safeInsets.top,
                            view.getPaddingRight(),
                            view.getPaddingBottom()
                    );
                    return windowInsets;
                }
        );
        ViewCompat.requestApplyInsets(topControls);
    }

    private static void applyTopMargin(View topControls) {
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) topControls.getLayoutParams();
        int initialTopMargin = params.topMargin;
        ViewCompat.setOnApplyWindowInsetsListener(topControls, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams updated =
                    (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            int desired = initialTopMargin + safe.top;
            if (updated.topMargin != desired) {
                updated.topMargin = desired;
                view.setLayoutParams(updated);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(topControls);
    }

    private static void applyBottomPadding(View bottomControls) {
        int initialBottomPadding = bottomControls.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(
                bottomControls,
                (view, windowInsets) -> {
                    Insets safeInsets = windowInsets.getInsets(
                            WindowInsetsCompat.Type.navigationBars()
                                    | WindowInsetsCompat.Type.systemGestures()
                    );
                    view.setPadding(
                            view.getPaddingLeft(),
                            view.getPaddingTop(),
                            view.getPaddingRight(),
                            initialBottomPadding + safeInsets.bottom
                    );
                    return windowInsets;
                }
        );
        ViewCompat.requestApplyInsets(bottomControls);
    }

    private static void applyBottomMargin(View bottomControls) {
        ViewGroup.LayoutParams rawParams = bottomControls.getLayoutParams();
        if (!(rawParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        int initialBottomMargin =
                ((ViewGroup.MarginLayoutParams) rawParams).bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(
                bottomControls,
                (view, windowInsets) -> {
                    Insets safeInsets = windowInsets.getInsets(
                            WindowInsetsCompat.Type.navigationBars()
                                    | WindowInsetsCompat.Type.systemGestures()
                    );
                    ViewGroup.MarginLayoutParams params =
                            (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                    params.bottomMargin = initialBottomMargin + safeInsets.bottom;
                    view.setLayoutParams(params);
                    return windowInsets;
                }
        );
        ViewCompat.requestApplyInsets(bottomControls);
    }
}
