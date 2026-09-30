package com.shutternote;

import android.content.Context;

import java.io.File;

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
    }

    /** Keep recent captures available for activity/process restoration. */
    public static void clearExpired(Context context) {
        File[] files = getDirectory(context).listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) deleteQuietly(file);
        }
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
