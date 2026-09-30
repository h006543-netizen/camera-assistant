package com.shutternote;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 촬영 결과를 확인하는 동안에만 사용하는 이미지 전용 캐시를 관리한다.
 */
public final class CameraSessionCache {

    private static final String DIRECTORY_NAME = "camera_sessions";
    private static final String LEGACY_PREFIX = "exposure_";

    private CameraSessionCache() {
    }

    public static File createCaptureFile(Context context) {
        File directory = getDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("촬영 캐시 폴더를 만들 수 없습니다.");
        }
        return new File(
                directory,
                LEGACY_PREFIX + System.currentTimeMillis() + ".jpg"
        );
    }

    public static void clearAll(Context context) {
        File directory = getDirectory(context);
        deleteChildren(directory);

        /*
         * 이전 구현이 cache 루트에 만들었던 exposure_*.jpg도 함께 정리한다.
         * 앱의 다른 캐시나 라이브러리 캐시는 삭제하지 않는다.
         */
        File cacheRoot = context.getCacheDir();
        File[] legacyFiles = cacheRoot.listFiles(file ->
                file.isFile()
                        && file.getName().startsWith(LEGACY_PREFIX)
                        && file.getName().endsWith(".jpg")
        );
        if (legacyFiles != null) {
            for (File file : legacyFiles) {
                deleteQuietly(file);
            }
        }
        clearMissingSaveRecords(context);
    }

    /** Keep recent captures available for activity/process restoration. */
    public static void clearExpired(Context context) {
        File[] files = getDirectory(context).listFiles();
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoff) deleteQuietly(file);
            }
        }
        clearMissingSaveRecords(context);
    }

    private static void clearMissingSaveRecords(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("capture_saves", Context.MODE_PRIVATE);
        List<String> missing = missingCapturePaths(preferences.getAll().keySet());
        if (missing.isEmpty()) return;
        SharedPreferences.Editor editor = preferences.edit();
        for (String path : missing) editor.remove(path);
        editor.apply();
    }

    /** Keep records for files that still exist, including captures whose deletion failed. */
    static List<String> missingCapturePaths(Iterable<String> paths) {
        List<String> missing = new ArrayList<>();
        for (String path : paths) {
            if (!new File(path).isFile()) missing.add(path);
        }
        return missing;
    }

    private static File getDirectory(Context context) {
        return new File(context.getCacheDir(), DIRECTORY_NAME);
    }

    private static void deleteChildren(File directory) {
        if (!directory.exists()) {
            return;
        }

        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteChildren(child);
                }
                deleteQuietly(child);
            }
        }
        deleteQuietly(directory);
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
