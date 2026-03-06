package com.khope.product.cache

import org.springframework.cache.Cache
import java.util.concurrent.Callable


class TieredCache (
    private val name: String,
    private val localCache: Cache,
    private val redisCache: Cache
) : Cache {
    override fun getName(): String = name

    override fun getNativeCache(): Any = this

    override fun get(key: Any): Cache.ValueWrapper? {
        val localWrapper = localCache.get(key)
        if (localWrapper != null) return localWrapper

        val redisWrapper = redisCache.get(key)
        if (redisWrapper != null) {
            localCache.put(key, redisWrapper.get())
            return redisWrapper
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, type: Class<T?>?): T? {
        val localValue = localCache.get(key, type)
        if (localValue != null) return localValue

        val redisValue = redisCache.get(key, type)
        if (redisValue != null) {
            localCache.put(key, redisValue)
            return redisValue
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, valueLoader: Callable<T?>): T? {
        val localValue = localCache.get(key, null as Class<T>?)
        if (localValue != null) return localValue

        val redisCacheValue = redisCache.get(key, null as Class<T>?)
        if (redisCacheValue != null) {
            localCache.put(key, redisCacheValue)
            return redisCacheValue
        }

        return try {
            val loadedValue = valueLoader.call()
            if (loadedValue != null) {
                put(key, loadedValue)
            }
            loadedValue
        } catch (e: Exception) {
            throw Cache.ValueRetrievalException(key, valueLoader, e)
        }
    }

    override fun put(key: Any, value: Any?) {
        redisCache.put(key, value)
        localCache.put(key, value)
    }

    override fun evict(key: Any) {
        redisCache.evict(key)
        localCache.evict(key)
    }

    override fun clear() {
        redisCache.clear()
        localCache.clear()
    }
}