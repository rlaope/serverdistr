package com.khope.common.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.khope.common.cache.TieredCacheProperties.CacheMode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext

@EnableCaching
@Configuration
@ConditionalOnProperty(prefix = "khope.cache", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TieredCacheProperties::class)
class TieredCacheAutoConfiguration {

    @Bean
    @Primary
    fun khopeCacheManager(
        properties: TieredCacheProperties,
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): CacheManager {
        val cacheSpecs = properties.caches

        if (cacheSpecs.isEmpty()) {
            val manager = SimpleCacheManager()
            manager.setCaches(emptyList())
            return manager
        }

        val redisSpecs = cacheSpecs.filter { it.value.mode != CacheMode.CAFFEINE }
        val redisCacheManager = buildRedisCacheManager(redisSpecs, connectionFactory, objectMapper)

        val caches = cacheSpecs.map { (name, spec) ->
            buildCache(name, spec, redisCacheManager)
        }

        val manager = SimpleCacheManager()
        manager.setCaches(caches)
        return manager
    }

    private fun buildCache(
        name: String,
        spec: TieredCacheProperties.CacheSpec,
        redisCacheManager: RedisCacheManager?,
    ): Cache {
        return when (spec.mode) {
            CacheMode.CAFFEINE -> buildCaffeineCache(name, spec)

            CacheMode.REDIS -> redisCacheManager?.getCache(name)
                ?: throw IllegalStateException("Redis cache not found: $name")

            CacheMode.TIERED -> {
                val l1 = buildCaffeineCache(name, spec)
                val l2 = redisCacheManager?.getCache(name)
                    ?: throw IllegalStateException("Redis cache not found: $name")
                TieredCache(name, l1, l2)
            }
        }
    }

    private fun buildCaffeineCache(name: String, spec: TieredCacheProperties.CacheSpec): CaffeineCache {
        val native = Caffeine.newBuilder()
            .maximumSize(spec.caffeineMaxSize)
            .expireAfterWrite(spec.caffeineTtl)
            .recordStats()
            .build<Any, Any>()
        return CaffeineCache(name, native)
    }

    private fun buildRedisCacheManager(
        specs: Map<String, TieredCacheProperties.CacheSpec>,
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisCacheManager? {
        if (specs.isEmpty()) return null

        val cacheObjectMapper = objectMapper.copy().apply {
            activateDefaultTyping(
                polymorphicTypeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY,
            )
        }

        val configs = specs.mapValues { (_, spec) ->
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(spec.redisTtl)
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(GenericJackson2JsonRedisSerializer(cacheObjectMapper))
                )
        }

        val manager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
            .withInitialCacheConfigurations(configs)
            .build()
        manager.afterPropertiesSet()
        return manager
    }
}
