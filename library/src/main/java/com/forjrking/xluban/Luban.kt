package com.forjrking.xluban

import android.content.Context
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import com.forjrking.xluban.io.InputStreamAdapter
import com.forjrking.xluban.io.InputStreamProvider
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * @description:
 * @author: forjrking
 * @date: 2020/10/10 3:58 PM
 */
class Luban(private val owner: LifecycleOwner) {

    companion object {
        lateinit var context: Context
        private const val DEFAULT_DISK_CACHE_DIR = "luban_disk_cache"

        fun with(context: FragmentActivity): Luban {
            return with(context as LifecycleOwner)
        }

        fun with(fragment: Fragment): Luban {
            return with(fragment.viewLifecycleOwner)
        }

        fun with(owner: LifecycleOwner = ProcessLifecycleOwner.get()): Luban {
            if (!::context.isInitialized) {
                if (owner is Context) {
                    context = owner.applicationContext
                } else {
                    //反射获取了
                }
            }
            return Luban(owner)
        }
    }

    private val compressFormat = CompressFormat.JPEG
    private val bestQuality = 100
    private var mOutPutDir: String? = null
    private var compress4Sample = true //使用采样率压缩
    private var mIgnoreSize = 100      //100kb
    private var mCompressFormat: CompressFormat? = null //输出格式
    private var mRenameListener: OnRenameListener? = null
    private var mSingleLiveData = CompressLiveData<File>()
    private var mMultiLiveData = CompressLiveData<MutableList<File>>()
    private var mCompressionPredicate: ((String) -> Boolean)? = null
    private val mStreamProviders: MutableList<InputStreamProvider> = ArrayList()

    fun load(inputStreamProvider: InputStreamProvider): Luban {
        mStreamProviders.add(inputStreamProvider)
        return this
    }

    fun load(file: File): Luban {
        mStreamProviders.add(object : InputStreamAdapter() {
            @Throws(IOException::class)
            override fun openInternal(): InputStream {
                return FileInputStream(file)
            }

            override val path: String
                get() = file.absolutePath
        })
        return this
    }

    fun load(string: String): Luban {
        mStreamProviders.add(object : InputStreamAdapter() {
            @Throws(IOException::class)
            override fun openInternal(): InputStream {
                return FileInputStream(string)
            }

            override val path: String
                get() = string
        })
        return this
    }

    fun load(uri: Uri): Luban {
        mStreamProviders.add(object : InputStreamAdapter() {

            @Throws(IOException::class)
            override fun openInternal(): InputStream {
                return context.contentResolver.openInputStream(uri)!!
            }

            override val path: String
                get() = uri.path!!
        })
        return this
    }

    fun <T> load(list: List<T>): Luban {
        for (src in list) {
            if (src is String) {
                load(src as String)
            } else if (src is File) {
                load(src as File)
            } else if (src is Uri) {
                load(src as Uri)
            } else {
                throw IllegalArgumentException("Incoming data type exception, it must be String, File, Uri")
            }
        }
        return this
    }

    fun setRenameListener(listener: OnRenameListener?): Luban {
        mRenameListener = listener
        return this
    }

    /**
     * 多文件压缩回调
     */
    fun observerMultiCompress(compressResult: CompressResult<MutableList<File>>.() -> Unit): Luban {
        mMultiLiveData.compressObserver(owner, compressResult)
        return this
    }

    /**
     * 单个文件压缩回调,多文件压缩设置此回调,会多次调用 onSuccess(..)
     */
    fun observerCompress(compressResult: CompressResult<File>.() -> Unit): Luban {
        mSingleLiveData.compressObserver(owner, compressResult)
        return this
    }

    fun setOutPutDir(outPutDir: String?): Luban {
        mOutPutDir = outPutDir
        return this
    }

    /**
     * 压缩后输出图片格式 只有3种支持,默认自动根据原图获取
     */
    fun format(compressFormat: CompressFormat): Luban {
        mCompressFormat = compressFormat
        return this
    }

    /**
     * 是否使用下采样压缩,如果不使用则使用双线性压缩方式
     * 默认使用向下采样压缩
     */
    fun useDownSample(compress4Sample: Boolean): Luban {
        this.compress4Sample = compress4Sample
        return this
    }

    /**
     * do not compress when the origin image file size less than one value
     *
     * @param size the value of file size, unit KB, default 100K
     */
    fun ignoreBy(size: Int): Luban {
        mIgnoreSize = size
        return this
    }

    /**
     * do compress image when return value was true, otherwise, do not compress the image file
     *
     * @param predicate A predicate callback that returns true or false for the given input path should be compressed.
     */
    fun filter(predicate: (String) -> Boolean): Luban {
        mCompressionPredicate = predicate
        return this
    }

    /**
     * begin compress image with asynchronous
     */
    fun launch() {

//            build().launch(context)
    }

//    inner class Builder constructor(private val context: Context) {
//   要不要用 build 模式困惑
//    }
}

@MainThread
fun <T> CompressLiveData<T>.compressObserver(owner: LifecycleOwner,
                                             compressResult: CompressResult<T>.() -> Unit
) {
    val result = CompressResult<T>();result.compressResult()
    observe(owner, androidx.lifecycle.Observer {
        when (it) {
            is State.Start -> {
                result.onStart()
            }
            is State.Success -> {
                result.onSuccess(it.data)
            }
            is State.Error -> {
                result.onError(it.error, it.file)
            }
        }
    })
}

class CompressResult<T> {
    var onStart: () -> Unit = {}
    var onSuccess: (data: T) -> Unit = {}
    var onError: (Throwable, File?) -> Unit = { e: Throwable, file: File? -> }
}

sealed class State<out T> {
    object Start : State<Nothing>()
    data class Success<out T>(val data: T) : State<T>()
    data class Error(val error: Throwable, val file: File? = null) : State<Nothing>()
}

typealias CompressLiveData<T> = MutableLiveData<State<T>>