package com.forjrking.lubankt.io

import com.forjrking.lubankt.Checker


/**
 * @description: 利用glide提供byte数组来节约内存
 * @author: forjrking
 * @date: 2020/10/10 10:26 AM
 */
object ArrayProvide {

    private val hasGlide: Boolean by lazy {
        try {
            //检查是否引入glide
            Class.forName("com.bumptech.glide.Glide")
            true
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun get(bufferSize: Int): ByteArray = if (hasGlide) {
        //反射判断是否包含glide
        val byteArrayPool = com.bumptech.glide.Glide.get(Checker.context).arrayPool
        byteArrayPool.get(bufferSize, ByteArray::class.java)
    } else {
        ByteArray(bufferSize)
    }

    @JvmStatic
    fun put(buf: ByteArray) {
        if (hasGlide && buf.isNotEmpty()) {
            //反射判断是否包含glide
            val byteArrayPool = com.bumptech.glide.Glide.get(Checker.context).arrayPool
            byteArrayPool.put(buf)
        }
    }

}
