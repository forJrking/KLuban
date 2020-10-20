package com.forjrking.xluban

import com.forjrking.xluban.ext.State
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test

import org.junit.Assert.*
import java.io.IOException

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
        providers.asFlow()
                .map {
                    val s = it.toString()
                    delay(500L * (8-it))
                    println("map: $it  t:${Thread.currentThread().name}")
//                    if(s =="4"){
//                        throw IOException("ssssss")
//                    }
                    s
                }
                .buffer()
                .flowOn(Dispatchers.Default)
                .onStart {
                    println("onStart: t:${Thread.currentThread().name}")
                }.onCompletion {
                    println("onCompletion: $it  t:${Thread.currentThread().name}")
                }.catch {
                    println("catch: $it  t:${Thread.currentThread().name}")
                }.collect {
                    println("success: $it  t:${Thread.currentThread().name}")
                }
    }

}