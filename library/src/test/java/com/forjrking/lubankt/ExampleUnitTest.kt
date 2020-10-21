package com.forjrking.lubankt

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test

import org.junit.Assert.*
import java.io.IOException
import java.util.concurrent.Executors
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

    val customerDispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    suspend fun compressV(int: Int): String = withContext(customerDispatcher) {
        //模拟压缩文件
        println("compress 开始:$int \tthread:${Thread.currentThread().name}")
        Thread.sleep(300)
        if (int ==8) {
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
    fun testBinFa() = runBlocking {
        val time = measureTimeMillis {
            listOf(1, 2, 3,4,5).asFlow().map { i ->
                async(customerDispatcher) {
                    compressV(i)
                }
            }.flowOn(Dispatchers.Default).map {
                it.await()
            }.buffer().onStart {
                println("onStart: t:${Thread.currentThread().name}")
            }.onCompletion {
                println("onCompletion: $it  t:${Thread.currentThread().name}")
            }.onEach {
                println("success: $it  t:${Thread.currentThread().name}")
            }.catch {
                println("catch: $it  t:${Thread.currentThread().name}")
            }.collect()
        }
        println("Collected in $time ms")
    }

    /** DES: 并行 */
    @Test
    fun testFlowFlat() = runBlocking<Unit> {
        val time = measureTimeMillis {
            listOf(1, 2, 3,4).asFlow().flatMapMerge {
                flow {
                    println("emit: $it  t:${Thread.currentThread().name}")
                    emit(compressV(it))
                }
            }.onStart {
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

}