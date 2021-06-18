package com.forjrking.lubankt.parser

import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream

/**
 * Uses [ExifInterface] to parse orientation data.
 *
 * ExifInterface supports the HEIF format on OMR1+. Glide's [DefaultImgHeaderParser]
 * doesn't currently support HEIF. In the future we should reconcile these two classes, but for now
 * this is a simple way to ensure that HEIF files are oriented correctly on platforms where they're
 * supported.
 */
internal class ExifInterfaceImgHeaderParser : ImgHeaderParser {

    override fun getType(input: InputStream): ImageType {
        return ImageType.UNKNOWN
    }

    @Throws(IOException::class)
    override fun getOrientation(input: InputStream): Int {
        val result = try {
            ExifInterface(input)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Throwable) {
            ImgHeaderParser.UNKNOWN_ORIENTATION
        }
        return if (result == ExifInterface.ORIENTATION_UNDEFINED) {
            ImgHeaderParser.UNKNOWN_ORIENTATION
        } else result
    }
}