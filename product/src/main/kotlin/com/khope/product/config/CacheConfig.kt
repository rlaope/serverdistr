package com.khope.product.config

import com.github.benmanes.caffeine.cache.Caffeine
import com.khope.product.cache.TieredCache
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCache
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class CacheConfig(
    private val connectionFactory: RedisConnectionFactory
) {

    @Bean
    fun caffeineCacheManager(): CacheManager {
        val manager = CaffeineCacheManager("products")
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
        )
        return manager
    }

    @Bean
    @Primary
    fun redisCacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(GenericJackson2JsonRedisSerializer())
            )
            .disableCachingNullValues()

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .withCacheConfiguration(
                "products",
                config.entryTtl(Duration.ofMinutes(30))
            )
            .build()
    }

    @Bean
    fun tieredCacheManager(): CacheManager {
        val cacheKeys = setOf("products", "users")

        val caffeineCaches = cacheKeys.map {
            val nativeCaffeine = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500)
                .build<Any, Any>()

            CaffeineCache(it, nativeCaffeine)
        }

        val redisCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeValuesWith(
                RedisSerializationContext
                    .SerializationPair
                    .fromSerializer(GenericJackson2JsonRedisSerializer())
            )

        val redisCacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(redisCacheConfig)
            .initialCacheNames(cacheKeys)
            .build()

        val tieredCaches = caffeineCaches.map { l1Cache ->
            val l2Cache = redisCacheManager.getCache(l1Cache.name)
                ?: throw IllegalStateException("Redis cache not found for ${l1Cache.name}")
            TieredCache(l1Cache.name, l1Cache, l2Cache)
        }

        val cacheManager = SimpleCacheManager()
        cacheManager.setCaches(tieredCaches)
        return cacheManager

    }
}
