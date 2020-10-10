package com.forjrking.xluban.parser;

import androidx.exifinterface.media.ExifInterface;
import java.io.IOException;
import java.io.InputStream;

/**
 * Uses {@link ExifInterface} to parse orientation data.
 *
 * <p>ExifInterface supports the HEIF format on OMR1+. Glide's {@link DefaultImgHeaderParser}
 * doesn't currently support HEIF. In the future we should reconcile these two classes, but for now
 * this is a simple way to ensure that HEIF files are oriented correctly on platforms where they're
 * supported.
 */
public final class ExifInterfaceImgHeaderParser implements ImgHeaderParser {

    @Override
    public ImageType getType(InputStream is) {
        return ImageType.UNKNOWN;
    }

    @Override
    public int getOrientation(InputStream is)
            throws IOException {
        ExifInterface exifInterface = new ExifInterface(is);
        int result = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (result == ExifInterface.ORIENTATION_UNDEFINED) {
            return ImgHeaderParser.UNKNOWN_ORIENTATION;
        }
        return result;
    }
}
