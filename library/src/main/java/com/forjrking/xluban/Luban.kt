package com.forjrking.xluban

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.forjrking.xluban.io.InputStreamAdapter
import com.forjrking.xluban.io.InputStreamProvider
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

class Luban {

    private var mTargetDir: String?
    private val mLeastCompressSize: Int
    private val mRenameListener: OnRenameListener?
    private val mCompressionPredicate: CompressionPredicate?
    private val mStreamProviders: MutableList<InputStreamProvider>?

    /**
     * Returns a file with a cache image name in the private cache directory.
     *
     * @param context A context.
     */
    private fun getImageCacheFile(context: Context, suffix: String): File {
        if (TextUtils.isEmpty(mTargetDir)) {
            mTargetDir = getImageCacheDir(context)!!.absolutePath
        }
        val cacheBuilder = mTargetDir + "/" +
                System.currentTimeMillis() +
                (Math.random() * 1000).toInt() +
                if (TextUtils.isEmpty(suffix)) ".jpg" else suffix
        return File(cacheBuilder)
    }

    private fun getImageCustomFile(context: Context, filename: String): File {
        if (TextUtils.isEmpty(mTargetDir)) {
            mTargetDir = getImageCacheDir(context)!!.absolutePath
        }
        val cacheBuilder = "$mTargetDir/$filename"
        return File(cacheBuilder)
    }

    /**
     * Returns a directory with a default name in the private cache directory of the application to
     * use to store retrieved audio.
     *
     * @param context A context.
     * @see .getImageCacheDir
     */
    private fun getImageCacheDir(context: Context): File? {
//        return getImageCacheDir(context, DEFAULT_DISK_CACHE_DIR)
    }

    /**
     * start asynchronous compress thread
     */
    private fun launch(context: Context) {
        if (mStreamProviders == null || mStreamProviders.size == 0 && mCompressListener != null) {
            mCompressListener!!.onError(NullPointerException("image file cannot be null"))
        }
    }

    /**
     * start compress and return the file
     */
    @Throws(IOException::class)
    private operator fun get(input: InputStreamProvider, context: Context): File? {
        return try {
            null
            //      return new Engine(input, getImageCacheFile(context, Checker.SINGLE.extSuffix(input)), focusAlpha).compress();
        } finally {
            input.close()
        }
    }

    @Throws(IOException::class)
    private operator fun get(context: Context): List<File?> {
        val results: MutableList<File?> = ArrayList()
        val iterator = mStreamProviders!!.iterator()
        while (iterator.hasNext()) {
            results.add(compress(context, iterator.next()))
            iterator.remove()
        }
        return results
    }

    @Throws(IOException::class)
    private fun compress(context: Context, path: InputStreamProvider): File? {
        return try {
            compressReal(context, path)
        } finally {
            path.close()
        }
    }

    @Throws(IOException::class)
    private fun compressReal(context: Context, path: InputStreamProvider): File? {
        //
//    File outFile = getImageCacheFile(context, Checker.SINGLE.extSuffix(path));
//
//    if (mRenameListener != null) {
//      String filename = mRenameListener.rename(path.getPath());
//      outFile = getImageCustomFile(context, filename);
//    }
//
//    if (mCompressionPredicate != null) {
//      if (mCompressionPredicate.apply(path.getPath())
//          && Checker.SINGLE.needCompress(mLeastCompressSize, path.getPath())) {
//        result = new Engine(path, outFile, focusAlpha).compress();
//      } else {
//        result = new File(path.getPath());
//      }
//    } else {
//      result = Checker.SINGLE.needCompress(mLeastCompressSize, path.getPath()) ?
//          new Engine(path, outFile, focusAlpha).compress() :
//          new File(path.getPath());
//    }
        return null
    }

}