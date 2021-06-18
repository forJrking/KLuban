package com.forjrking.lubankt.parser;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

import com.bumptech.glide.load.ImageHeaderParser;
import com.bumptech.glide.load.resource.bitmap.DefaultImageHeaderParser;

import java.io.IOException;
import java.io.InputStream;

/**
 * Uses {@link ExifInterface} to parse orientation data.
 *
 * <p>ExifInterface supports the HEIF format on OMR1+. Glide's {@link DefaultImageHeaderParser}
 * doesn't currently support HEIF. In the future we should reconcile these two classes, but for now
 * this is a simple way to ensure that HEIF files are oriented correctly on platforms where they're
 * supported.
 */
public final class ExifInterfaceImageHeaderParser implements ImgHeaderParser {

    @NonNull
    @Override
    public ImageType getType(@NonNull InputStream is) {
        return ImageType.UNKNOWN;
    }

    @Override
    public int getOrientation(@NonNull InputStream is)
            throws IOException {
        ExifInterface exifInterface = new ExifInterface(is);
        int result =
                exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (result == ExifInterface.ORIENTATION_UNDEFINED) {
            return ImageHeaderParser.UNKNOWN_ORIENTATION;
        }
        return result;
    }

}
