package com.factstore.config

object RegionContextHolder {
    private val threadLocal = ThreadLocal<String?>()

    fun set(region: String?) { threadLocal.set(region) }
    fun get(): String? = threadLocal.get()
    fun clear() { threadLocal.remove() }
}
