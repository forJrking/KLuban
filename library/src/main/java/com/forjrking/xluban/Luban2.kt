package com.forjrking.xluban

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import androidx.annotation.IntRange
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
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
class Luban2 private constructor(private val owner: LifecycleOwner) {

    companion object {

        private const val DEFAULT_DISK_CACHE_DIR = "luban_disk_cache"

        fun with(context: FragmentActivity): Luban2 {
            return with(context as LifecycleOwner)
        }

        fun with(fragment: Fragment): Luban2 {
            return with(fragment.viewLifecycleOwner)
        }

        fun with(owner: LifecycleOwner = ProcessLifecycleOwner.get()): Luban2 {
            return Luban2(owner)
        }
    }

    fun load(inputStream: InputStream) = loadGeneric(inputStream) { it }

    fun load(file: File) = loadGeneric(file) { FileInputStream(it) }

    fun load(path: String) = loadGeneric(path) { FileInputStream(it) }

    fun load(uri: Uri) = loadGeneric(uri) { Checker.context.contentResolver.openInputStream(it)!! }

    // 把数据转换成 流
    private fun <T> loadGeneric(vararg ts: T, transform: (T) -> InputStream): Builder<T, File> {
        val providers: MutableList<InputStreamProvider<T>> = ArrayList()
        ts.forEach {
            providers.add(object : InputStreamAdapter<T>() {
                @Throws(IOException::class)
                override fun openInternal(): InputStream {
                    return transform.invoke(src)
                }

                override val src: T
                    get() = it
            })
        }
        return Builder(owner, providers)
    }

    fun <T> load(vararg list: T): Builder<T, File> {
        val providers: MutableList<InputStreamProvider<T>> = ArrayList()
        list.forEach {
            providers.add(object : InputStreamAdapter<T>() {
                @Throws(IOException::class)
                override fun openInternal(): InputStream {
                    return when (src) {
                        is String -> {
                            FileInputStream(src as String)
                        }
                        is File -> {
                            FileInputStream(src as File)
                        }
                        is Uri -> {
                            Checker.context.contentResolver.openInputStream(src as Uri)!!
                        }
                        else -> {
                            throw IOException("Incoming data type exception, it must be String, File, Uri")
                        }
                    }
                }

                override val src: T
                    get() = it
            })
        }
        return Builder(owner, providers)
    }

    /** DES: T 表示源数据 R表示生成结果 */
    class Builder<T, R>(private val owner: LifecycleOwner, private val providers: MutableList<InputStreamProvider<T>>) {

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
        private var mSingleLiveData = CompressLiveData<R>()

        //压缩过滤器
        private var mCompressionPredicate: ((T) -> Boolean) = { true }


        fun filter(predicate: (T) -> Boolean): Builder<T, R> {
            mCompressionPredicate = predicate
            return this
        }

        fun rename(rename: ((String) -> String)?): Builder<T, R> {
            mRenamePredicate = rename
            return this
        }

//        /**
//         * 多文件压缩回调
//         */
//        fun observerMultiCompress(compressResult: CompressResult<MutableList<R>>.() -> Unit): Builder<T, R> {
//            mMultiLiveData.compressObserver(owner, compressResult)
//            return this
//        }

        /**
         * 单个文件压缩回调,多文件压缩设置此回调,会多次调用 onSuccess(..)
         */
        fun compressObserver(compressResult: CompressResult<R>.() -> Unit): Builder<T, R> {
            mSingleLiveData.compressObserver(owner, compressResult)
            return this
        }

        fun setOutPutDir(outPutDir: String?): Builder<T, R> {
            mOutPutDir = outPutDir
            return this
        }

        /**
         * 压缩后输出图片格式 只有3种支持,默认自动根据原图获取
         */
        fun format(compressFormat: CompressFormat): Builder<T, R> {
            mCompressFormat = compressFormat
            return this
        }

        /**
         * 是否使用下采样压缩,如果不使用则使用双线性压缩方式
         * 默认使用向下采样压缩
         */
        fun useDownSample(compress4Sample: Boolean): Builder<T, R> {
            this.mCompress4Sample = compress4Sample
            return this
        }

        /**压缩质量0~100*/
        fun quality(@IntRange(from = 1, to = 100) quality: Int): Builder<T, R> {
            bestQuality = quality
            return this
        }

        /**
         * 大小忽略  默认 100kb
         */
        fun ignoreBy(size: Long): Builder<T, R> {
            mIgnoreSize = size
            return this
        }

        /**
         * begin compress image with asynchronous
         */
        fun launch() {
            //开启协程
            owner.lifecycleScope.launch {
                flow {
                    providers.forEach {
                        val result = compress(it)
                        emit(result as R)
                    }
                }.flowOn(Dispatchers.Default).buffer().onStart {
                    mSingleLiveData.value = State.Start
                }.onCompletion {
                    mSingleLiveData.value = State.Completion
                }.catch {
                    mSingleLiveData.value = State.Error(it)
                }.collect {
                    mSingleLiveData.value = State.Success(it)
                }
            }
        }

        @WorkerThread
        @Throws(IOException::class)
        private suspend fun compress(stream: InputStreamProvider<T>): File {
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
            return if (mCompressionPredicate.invoke(stream.src) && mIgnoreSize < length) {
                CompressEngine(stream, outFile, mCompress4Sample, mIgnoreSize, bestQuality, format, decodeConfig).compress()
            } else {
                //copy文件到临时文件
                FileOutputStream(outFile).use { fos ->
                    stream.rewindAndGet().copyTo(fos)
                }
                outFile
            }
        }

//        //同步方法对外提供
//        @Throws(IOException::class)
//        suspend fun get(index: Int = 0): File {
//            val it = providers.removeAt(index)
//            return try {
//                compress(it)
//            } finally {
//                it.close()
//            }
//        }
    }
}