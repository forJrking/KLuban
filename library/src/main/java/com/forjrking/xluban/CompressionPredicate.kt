package com.forjrking.xluban

/**
 * Created on 2018/1/3 19:43
 *
 * @author andy
 *
 * A functional interface (callback) that returns true or false for the given input path should be compressed.
 */
interface CompressionPredicate {
    /**
     * Determine the given input path should be compressed and return a boolean.
     * @param path input path
     * @return the boolean result
     */
    fun apply(path: String): Boolean
}