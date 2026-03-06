package com.khope.common.cache

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "khope.cache")
data class TieredCacheProperties(
    val enabled: Boolean = true,
    val caches: Map<String, CacheSpec> = emptyMap(),
) {
    data class CacheSpec(
        val mode: CacheMode = CacheMode.TIERED,
        val caffeineMaxSize: Long = 500,
        val caffeineTtl: Duration = Duration.ofMinutes(5),
        val redisTtl: Duration = Duration.ofMinutes(30),
    )

    enum class CacheMode {
        CAFFEINE,  // L1 only (local)
        REDIS,     // L2 only (distributed)
        TIERED,    // L1 + L2
    }
}
