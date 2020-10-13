package com.forjrking.xluban.ext

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData

/**
 * @description: 扩展
 * @author: 岛主
 * @date: 2020/10/13 23:28
 * @version: 1.0.0
 */

@MainThread
fun <T, R> CompressLiveData<T, R>.compressObserver(owner: LifecycleOwner,
                                                   compressResult: CompressResult<T, R>.() -> Unit
) {
    val result = CompressResult<T, R>();result.compressResult()
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
                result.onError(it.error, it.src)
            }
        }
    })
}

class CompressResult<T, R> {
    var onStart: () -> Unit = {}
    var onCompletion: () -> Unit = {}
    var onSuccess: (data: R) -> Unit = {}
    var onError: (Throwable, T?) -> Unit = { _: Throwable, _: T? -> }
}

sealed class State<out T, out R> {
    object Start : State<Nothing, Nothing>()
    object Completion : State<Nothing, Nothing>()
    data class Success<out R>(val data: R) : State<Nothing, R>()
    data class Error<out T>(val error: Throwable, val src: T? = null) : State<T, Nothing>()
}

typealias CompressLiveData<T, R> = MutableLiveData<State<T, R>>