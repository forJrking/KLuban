package com.forjrking.lubankt

import com.forjrking.lubankt.ext.State
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test

import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.measureTimeMillis

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    val customerDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    suspend fun compressV(int: Int): String = withContext(customerDispatcher) {
        //模拟压缩文件
        println("compress 开始:$int \tthread:${Thread.currentThread().name}")
        Thread.sleep(300)
        if (int == 2) {
            throw IOException("error $int")
        }
        val toString = int.toString() + "R"
        println("compress 结束:${toString} \tthread:${Thread.currentThread().name}")
        return@withContext toString
    }

    /** DES: 模拟压缩逻辑
     * 1. 7张图片
     * 2.
     * */
    @Test
    fun testFlow() = runBlocking<Unit> {
        val providers = listOf(1, 2, 3, 4)
        val time = measureTimeMillis {
            providers.asFlow().map {
                compressV(it)
            }.flowOn(Dispatchers.Default).onStart {
                println("onStart: t:${Thread.currentThread().name}")
            }.onCompletion {
                println("onCompletion: $it  t:${Thread.currentThread().name}")
            }.catch {
                println("catch: $it  t:${Thread.currentThread().name}")
            }.collect {
                println("success: $it  t:${Thread.currentThread().name}")
            }
        }
        println("time: $time")
    }


    /** DES: 并发 */
    @Test
    fun testBinFa() {

        val handler = CoroutineExceptionHandler { _, exception ->
            println("Caught $exception")
        }
        val dispatchers = if (true) EmptyCoroutineContext else customerDispatcher
        val scope = GlobalScope
        scope.launch(handler) {
            val time = measureTimeMillis {

                val listOf = listOf(1, 2, 3, 4, 6, 7, 8)
                val taskFlow = if (false) {
                    listOf.map {
                        async { compressV(it) }
                    }.asFlow().map {
                        it.await()
                    }
                } else {
                    flow {
                        listOf.forEach {
                            emit(compressV(it))
                        }
                    }
                }

                taskFlow.onStart {
                    println("onStart: t:${Thread.currentThread().name}")
                }.onCompletion {
                    println("onCompletion: $it  t:${Thread.currentThread().name}")
                    //成功和错误回调在后也 可以调前
                    if (it != null) println("catch: $it  t:${Thread.currentThread().name}")
                }.onEach {
                    //排序好的结果收集
                    println("success: $it  t:${Thread.currentThread().name}")
                }.collect()
            }
            println("time: $time")
        }

        println(">>>>>>>>>END") // 主线程中的代码会立即执行
        runBlocking {     // 但是这个表达式阻塞了主线程
            delay(5000L)  // ……我们延迟 2 秒来保证 JVM 的存活
        }
    }

    /** DES: 并行 */
    @Test
    fun testFlowFlat() = runBlocking<Unit> {
        val scope = this
        val time = measureTimeMillis {
            listOf(1, 2, 3, 4).asFlow().flatMapMerge {
                flow {
                    println("emit: $it  t:${Thread.currentThread().name}")
                    if (scope.isActive) {
                        emit(compressV(it))
                    }
                }.flowOn(EmptyCoroutineContext)
            }.onStart {
                println("onStart: t:${Thread.currentThread().name}")
            }.onCompletion {
                println("onCompletion: $it  t:${Thread.currentThread().name}")
            }.catch {
                println("catch: $it  t:${Thread.currentThread().name}")
            }.onEach {
                println("success: $it  t:${Thread.currentThread().name}")
            }.collect()
        }
        println("time: $time")
    }

}