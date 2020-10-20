package com.forjrking.xluban

import com.forjrking.xluban.ext.State
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


    @Test
    fun testFlow() = runBlocking<Unit> {
        val providers = listOf(1, 2, 3, 4, 5, 6, 7)
        val time = measureTimeMillis {

            flow {
                providers.onEach {
                    println("emit: $it  t:${Thread.currentThread().name}")
                    emit(it)
                }
            } .buffer().flowOn(Dispatchers.IO).map {
                delay(400)
                println("map: $it  t:${Thread.currentThread().name}")
                it.toString()
            }
                    .buffer()
                    .flowOn(Dispatchers.Default).onStart {
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

        val customerDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val time = measureTimeMillis {
            val providers = listOf(1, 2, 3, 4, 5, 6, 7)

            flow {
                providers.onEach {
                    emit(it)
                    println("emit:$it thread :${Thread.currentThread().name}")
                }
            }.map { i ->
                val r = async(customerDispatcher) {
                    delay(200)
                    println("async:$i thread :${Thread.currentThread().name}")
                    if (i == 4 || i == 6) {
                        throw IOException()
                    }
                    i
                }

                r
            }.buffer().collect { value ->
//                println("collect S $value")
//                        delay(300)
                try {
                    println("collect  ${value.await()}")
                } catch (e: Exception) {
                    println("Error ${value}")
                }
            }
        }
        println("Collected in $time ms")
    }

    /** DES: 并行 */
    @Test
    fun testFlowFlat() = runBlocking<Unit> {
        val providers = listOf(1, 2, 3, 4, 5, 6, 7)
        //创建一个一次只能执行2个任务的线程池
        val customerDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val time = measureTimeMillis {
            providers.asFlow().flatMapMerge {
                flow {
                    println("emit: $it  t:${Thread.currentThread().name}")
                    delay(500)
                    emit(it)
                }.flowOn(customerDispatcher)
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