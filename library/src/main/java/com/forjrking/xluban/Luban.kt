package com.forjrking.xluban

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import androidx.annotation.IntRange
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.forjrking.xluban.io.InputStreamAdapter
import com.forjrking.xluban.io.InputStreamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.*
import kotlin.jvm.Throws

/**
 * @description:
 * @author: forjrking
 * @date: 2020/10/10 3:58 PM
 */
class Luban private constructor(private val owner: LifecycleOwner) {

    companion object {

        private const val DEFAULT_DISK_CACHE_DIR = "luban_disk_cache"

        fun with(context: FragmentActivity): Luban {
            return with(context as LifecycleOwner)
        }

        fun with(fragment: Fragment): Luban {
            return with(fragment.viewLifecycleOwner)
        }

        fun with(owner: LifecycleOwner = ProcessLifecycleOwner.get()): Luban {
            return Luban(owner)
        }
    }

    //质量压缩质量系数 0~100 无损压缩无用
    private var bestQuality = Checker.calculateQuality(Checker.context)
    //输出目录
    private var mOutPutDir: String? = Checker.getCacheDir(Checker.context, DEFAULT_DISK_CACHE_DIR)?.absolutePath
    // 使用采样率压缩 or 双线性压缩
    private var mCompress4Sample = true
    // 忽略压缩大小
    private var mIgnoreSize = 100 * 1024L
    //输出格式
    private var mCompressFormat: CompressFormat? = null
    // 重命名或文件重定向
    private var mRenamePredicate: ((String) -> String)? = null
    // 单个订阅监听
    private var mSingleLiveData = CompressLiveData<File>()
    // 多个文件订阅监听
    private var mMultiLiveData = CompressLiveData<MutableList<File>>()
    //压缩过滤器
    private var mCompressionPredicate: ((InputStreamProvider<*>) -> Boolean) = { true }
    //元数据集合
    private val mStreamProviders: MutableList<InputStreamProvider<*>> = ArrayList()

    fun <T> load(inputStreamProvider: InputStreamProvider<T>): Luban {
        mStreamProviders.add(inputStreamProvider)
        return this
    }

    fun load(file: File): Luban {
        mStreamProviders.add(object : InputStreamAdapter<File>() {
            @Throws(IOException::class)
            override fun openInternal(): InputStream {
                return FileInputStream(file)
            }

            override val src: File?
                get() = file
        })
        return this
    }

    fun load(string: String): Luban {
        mStreamProviders.add(object : InputStreamAdapter<String>() {
            @Throws(IOException::class)
            override fun openInternal(): InputStream {
                return FileInputStream(string)
            }

            override val src: String?
                get() = string
        })
        return this
    }

    fun load(uri: Uri): Luban {
        mStreamProviders.add(object : InputStreamAdapter<Uri>() {
            @Throws(IOException::class)
            override fun openInternal(): InputStream {
                return Checker.context.contentResolver.openInputStream(uri)!!
            }

            override val src: Uri?
                get() = uri
        })
        return this
    }

    fun <T> load(vararg list: T): Luban {
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

    fun rename(rename: ((String) -> String)?): Luban {
        mRenamePredicate = rename
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
        this.mCompress4Sample = compress4Sample
        return this
    }

    /**压缩质量0~100*/
    fun quality(@IntRange(from = 1, to = 100) quality: Int): Luban {
        bestQuality = quality
        return this
    }

    /**
     * 大小忽略  默认 100kb
     */
    fun ignoreBy(size: Long): Luban {
        mIgnoreSize = size
        return this
    }

    /**
     * do compress image when return value was true, otherwise, do not compress the image file
     *
     * @param predicate A predicate callback that returns true or false for the given input path should be compressed.
     */
    fun filter(predicate: (InputStreamProvider<*>) -> Boolean): Luban {
        mCompressionPredicate = predicate
        return this
    }

    /**
     * begin compress image with asynchronous
     */
    fun launch() {
        //开启协程
        owner.lifecycleScope.launch {
            val files = mutableListOf<File>()
            flow {
                mStreamProviders.forEach {
                    val result = compress(it)
                    emit(result)
                }
            }.flowOn(Dispatchers.Default).buffer().onStart {
                mSingleLiveData.value = State.Start
                mMultiLiveData.value = State.Start
            }.onCompletion {
                mSingleLiveData.value = State.Completion
                //全部发送
                mMultiLiveData.value = State.Success(files)
                mMultiLiveData.value = State.Completion
            }.catch {
                mSingleLiveData.value = State.Error(it)
                mMultiLiveData.value = State.Error(it)
            }.collect {
                files.add(it)
                mSingleLiveData.value = State.Success(it)
            }
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    private suspend fun compress(stream: InputStreamProvider<*>): File {
        if (mOutPutDir.isNullOrEmpty()) {
            throw IOException("mOutPutDir cannot be null or check permissions")
        }
        //后缀
        val srcStream = stream.rewindAndGet()
        val length = srcStream.available()
        val type = Checker.getType(srcStream)
        //组合一个名字给输出文件
        val cacheFile = "$mOutPutDir/${System.nanoTime()}.${type.suffix}"
        val outFile = if (mRenamePredicate != null) {
//            重命名
            File(mRenamePredicate!!.invoke(cacheFile))
        } else {
            File(cacheFile)
        }
        //如果没有指定format 智能获取解码结果
        val format = mCompressFormat ?: type.format
        //图片是否带有透明层
        val decodeConfig = if (type.hasAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

        //判断过滤器 开始压缩
        return if (mCompressionPredicate.invoke(stream) && mIgnoreSize < length) {
            CompressEngine(stream, outFile, mCompress4Sample, mIgnoreSize, bestQuality, format, decodeConfig).compress()
        } else {
            //copy文件到临时文件
            FileOutputStream(outFile).use { fos ->
                stream.rewindAndGet().copyTo(fos)
            }
            outFile
        }
    }

    //同步方法对外提供
    @Throws(IOException::class)
    suspend fun get(index: Int = 0): File {
        val it = mStreamProviders.removeAt(index)
        return try {
            compress(it)
        } finally {
            it.close()
        }
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
            is State.Completion -> {
                result.onCompletion()
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
    var onCompletion: () -> Unit = {}
    var onSuccess: (data: T) -> Unit = {}
    var onError: (Throwable, File?) -> Unit = { e: Throwable, file: File? -> }
}

sealed class State<out T> {
    object Start : State<Nothing>()
    object Completion : State<Nothing>()
    data class Success<out T>(val data: T) : State<T>()
    data class Error(val error: Throwable, val file: File? = null) : State<Nothing>()
}

typealias CompressLiveData<T> = MutableLiveData<State<T>>