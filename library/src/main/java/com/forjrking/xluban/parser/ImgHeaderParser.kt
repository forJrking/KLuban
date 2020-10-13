package com.forjrking.xluban.parser

import android.graphics.Bitmap
import java.io.IOException
import java.io.InputStream
import kotlin.jvm.Throws

/** Interface for the ImageHeaderParser.  */
interface ImgHeaderParser {

    @Throws(IOException::class)
    fun getType(input: InputStream): ImageType

    /**
     * Parse the orientation from the image header. If it doesn't handle this image type (or this is
     * not an image) it will return a default value rather than throwing an exception.
     *
     * @return The exif orientation if present or -1 if the header couldn't be parsed or doesn't
     * contain an orientation
     */
    @Throws(IOException::class)
    fun getOrientation(input: InputStream): Int

    companion object {
        /**
         * A constant indicating we were unable to parse the orientation from the image either because no
         * exif segment containing orientation data existed, or because of an I/O error attempting to read
         * the exif segment.
         */
        const val UNKNOWN_ORIENTATION = -1
    }
}

/**
 * The format of the image data including whether or not the image may include transparent pixels.
 */
enum class ImageType(val suffix: String, val hasAlpha: Boolean, val format: Bitmap.CompressFormat) {

    GIF("gif", true, Bitmap.CompressFormat.PNG),
    JPEG("jpg", false, Bitmap.CompressFormat.JPEG),
    RAW("raw", false, Bitmap.CompressFormat.JPEG),

    /* PNG type with alpha.  */
    PNG_A("png", true, Bitmap.CompressFormat.PNG),

    /* PNG type without alpha.  */
    PNG("png", false, Bitmap.CompressFormat.PNG),

    /* WebP type with alpha.  */
    WEBP_A("webp", true, Bitmap.CompressFormat.WEBP),

    /* WebP type without alpha.  */
    WEBP("webp", false, Bitmap.CompressFormat.WEBP),

    /* Unrecognized type.  */
    UNKNOWN("unknown", false, Bitmap.CompressFormat.JPEG);

}

