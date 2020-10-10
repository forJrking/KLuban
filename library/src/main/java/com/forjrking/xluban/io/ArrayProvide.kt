package com.forjrking.xluban.io

import android.app.Application
import android.content.Context


/**
 * @description: 利用glide提供byte数组来节约内存
 * @author: forjrking
 * @date: 2020/10/10 10:26 AM
 */
object ArrayProvide {

    private val hasGlide: Boolean = try {
        //检查是否引入glide
        Class.forName("com.bumptech.glide.Glide")
        //检查是否可以反射获取 context
        reflectContext()
        true
    } catch (e: Exception) {
        false
    }

    @JvmStatic
    fun get(bufferSize: Int): ByteArray = if (hasGlide) {
        //反射判断是否包含glide
        val byteArrayPool = com.bumptech.glide.Glide.get(reflectContext()).arrayPool
        byteArrayPool.get(bufferSize, ByteArray::class.java)
    } else {
        ByteArray(bufferSize)
    }

    @JvmStatic
    fun put(buf: ByteArray) {
        if (hasGlide && buf.isNotEmpty()) {
            //反射判断是否包含glide
            val byteArrayPool = com.bumptech.glide.Glide.get(reflectContext()).arrayPool
            byteArrayPool.put(buf)
        }
    }

    private var context: Context? = null

    /**
     * DES: 反射获取全局Context  后期可能被google废弃这里会报错
     */
    private fun reflectContext(): Context {
        if (context == null) {
            try {
                return Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null) as Application
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                return Class.forName("android.app.AppGlobals")
                        .getMethod("getInitialApplication")
                        .invoke(null) as Application
            } catch (e: Exception) {
                e.printStackTrace()
            }
            throw IllegalStateException("reflect Context error")
        }
        return context!!
    }

}
