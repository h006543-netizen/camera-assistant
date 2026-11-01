package com.shutternote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import java.io.File;
import java.io.IOException;

/** Decode bounded display copies. Scan bytes and both photos' colours remain untouched. */
final class FilmScanImages {
    static Bitmap read(File file, int extraRotation, boolean readExif) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Photo missing");
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Unsupported image");
        Runtime runtime = Runtime.getRuntime();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = ImageMemoryBudget.sampleSize(bounds.outWidth, bounds.outHeight,
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()));
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2000) options.inSampleSize *= 2;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (decoded == null) throw new IOException("Cannot decode image");
        try {
            Matrix matrix = new Matrix();
            if (readExif) {
                int orientation;
                try { orientation = new ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL); }
                catch (IOException ignored) { orientation = ExifInterface.ORIENTATION_NORMAL; }
                switch (orientation) {
                    case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
                    case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                    case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
                    case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                    case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                    case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                    case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
                    default: break;
                }
            }
            matrix.postRotate(FilmFrameRecord.normalizedRotation(extraRotation));
            if (matrix.isIdentity()) return decoded;
            Bitmap result = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
            if (result != decoded) decoded.recycle();
            return result;
        } catch (RuntimeException | OutOfMemoryError error) { decoded.recycle(); throw error; }
    }
}
