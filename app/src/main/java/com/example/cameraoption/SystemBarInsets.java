package com.example.cameraoption;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

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
        applyTopPadding(topControls);
        applyBottomPadding(bottomControls);
    }

    public static void applyTopAndBottomMargin(
            Activity activity,
            View topControls,
            View bottomControls
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        applyTopPadding(topControls);
        applyBottomMargin(bottomControls);
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
