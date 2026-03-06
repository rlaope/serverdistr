package com.khope.common.cache

import org.springframework.cache.Cache
import java.util.concurrent.Callable

class TieredCache(
    private val name: String,
    private val localCache: Cache,
    private val redisCache: Cache,
) : Cache {

    override fun getName(): String = name

    override fun getNativeCache(): Any = this

    override fun get(key: Any): Cache.ValueWrapper? {
        localCache.get(key)?.let { return it }

        redisCache.get(key)?.let { wrapper ->
            localCache.put(key, wrapper.get())
            return wrapper
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, type: Class<T?>?): T? {
        localCache.get(key, type)?.let { return it }

        redisCache.get(key, type)?.let { value ->
            localCache.put(key, value)
            return value
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, valueLoader: Callable<T?>): T? {
        localCache.get(key, null as Class<T>?)?.let { return it }

        redisCache.get(key, null as Class<T>?)?.let { value ->
            localCache.put(key, value)
            return value
        }

        return try {
            val loaded = valueLoader.call()
            if (loaded != null) put(key, loaded)
            loaded
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
