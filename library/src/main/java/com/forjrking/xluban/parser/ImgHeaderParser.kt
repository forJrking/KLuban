package com.forjrking.xluban.parser

import java.io.IOException
import java.io.InputStream

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
enum class ImageType(private val img: String, private val hasAlpha: Boolean) {

    GIF("gif", true),
    JPEG("jpg", false),
    RAW("raw", false),

    /* PNG type with alpha.  */
    PNG_A("png", true),

    /* PNG type without alpha.  */
    PNG("png", false),

    /* WebP type with alpha.  */
    WEBP_A("webp", true),

    /* WebP type without alpha.  */
    WEBP("webp", false),

    /* Unrecognized type.  */
    UNKNOWN("jpg", false);

    fun hasAlpha(): Boolean {
        return hasAlpha
    }

    fun suffix(): String {
        return img
    }
}

