package com.forjrking.xluban

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.forjrking.xluban.io.BufferedInputStreamWrap
import com.forjrking.xluban.parser.DefaultImgHeaderParser
import com.forjrking.xluban.parser.ExifInterfaceImgHeaderParser
import com.forjrking.xluban.parser.ImageType
import com.forjrking.xluban.parser.ImgHeaderParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.jvm.Throws

internal object Checker {

    // Right now we're only using this parser for HEIF images, which are only supported on OMR1+.
    // If we need this for other file types, we should consider removing this restriction.
    private val parsers: List<ImgHeaderParser> by lazy {
        mutableListOf<ImgHeaderParser>().apply {
            add(DefaultImgHeaderParser())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                //支持HEIF
                add(ExifInterfaceImgHeaderParser())
            }
        }
    }

    const val TAG = "Luban"

    //常用压缩比
    private const val DEFAULT_QUALITY = 66
    private const val DEFAULT_LOW_QUALITY = 60
    private const val DEFAULT_HEIGHT_QUALITY = 82
    private const val DEFAULT_X_HEIGHT_QUALITY = 88
    private const val DEFAULT_XX_HEIGHT_QUALITY = 94

    /**
     * @return 根据手机的计算合适的压缩率
     */
    fun calculateQuality(context: Context): Int {
        val dm = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(dm)
        val density = dm.density
        return if (density > 3f) {
            DEFAULT_LOW_QUALITY
        } else if (density > 2.5f && density <= 3f) {
            DEFAULT_QUALITY
        } else if (density > 2f && density <= 2.5f) {
            DEFAULT_HEIGHT_QUALITY
        } else if (density > 1.5f && density <= 2f) {
            DEFAULT_X_HEIGHT_QUALITY
        } else {
            DEFAULT_XX_HEIGHT_QUALITY
        }
    }

    /**
     * Returns a directory with the given name in the private cache directory of the application to
     * use to store retrieved media and thumbnails.
     *
     * @param context   A context.
     * @param cacheName The name of the subdirectory in which to store the cache.
     * @see .getImageCacheDir
     */
    fun getCacheDir(context: Context, cacheName: String): File? {
        val cacheDir = context.externalCacheDir
        if (cacheDir != null) {
            val result = File(cacheDir, cacheName)
            return if (!result.mkdirs() && (!result.exists() || !result.isDirectory)) {
                // File wasn't able to create a directory, or the result exists but not a directory
                null
            } else result
        }
        return null
    }

    const val MARK_READ_LIMIT = 5 * 1024 * 1024

    @Throws(IOException::class)
    fun getType(stream: InputStream?): ImageType {
        var inputStream = stream ?: return ImageType.UNKNOWN
        if (!inputStream.markSupported()) {
            inputStream = BufferedInputStreamWrap(inputStream)
        }
        inputStream.mark(MARK_READ_LIMIT)
        //解析器
        val reader = object : TypeReader {
            override fun getType(parser: ImgHeaderParser): ImageType {
                return try {
                    parser.getType(inputStream)
                } finally {
                    inputStream.reset()
                }
            }
        }
        return getTypeInternal(parsers, reader)
    }

    @Throws(IOException::class)
    fun getOrientation(stream: InputStream?): Int {
        var inputStream = stream ?: return ImgHeaderParser.UNKNOWN_ORIENTATION
        if (!inputStream.markSupported()) {
            inputStream = BufferedInputStreamWrap(inputStream)
        }
        inputStream.mark(MARK_READ_LIMIT)

        val reader = object : OrientationReader {
            override fun getOrientation(parser: ImgHeaderParser): Int {
                return try {
                    parser.getOrientation(inputStream)
                } finally {
                    inputStream.reset()
                }
            }
        }
        return getOrientationInternal(parsers, reader)
    }

    @Throws(IOException::class)
    private fun getTypeInternal(parsers: List<ImgHeaderParser>, reader: TypeReader): ImageType {
        parsers.forEach { parser ->
            val type = reader.getType(parser)
            if (type != ImageType.UNKNOWN) {
                return type
            }
        }
        return ImageType.UNKNOWN
    }

    @Throws(IOException::class)
    private fun getOrientationInternal(parsers: List<ImgHeaderParser>, reader: OrientationReader): Int {
        parsers.forEach { parser ->
            val orientation = reader.getOrientation(parser)
            if (orientation != ImgHeaderParser.UNKNOWN_ORIENTATION) {
                return orientation
            }
        }
        return ImgHeaderParser.UNKNOWN_ORIENTATION
    }

    private interface TypeReader {
        @Throws(IOException::class)
        fun getType(parser: ImgHeaderParser): ImageType
    }

    private interface OrientationReader {
        @Throws(IOException::class)
        fun getOrientation(parser: ImgHeaderParser): Int
    }

}