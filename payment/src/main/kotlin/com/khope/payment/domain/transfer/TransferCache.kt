package com.khope.payment.domain.transfer

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class TransferCache(
    private val redissonClient: RedissonClient
) {

    companion object {
        private const val PREFIX = "transfer:daily"
    }

    private fun bucketKey(key: String): String = "$PREFIX:$key:${LocalDate.now()}"

    fun getDailyUsage(key: String): BigDecimal {
        return redissonClient.getBucket<String>(bucketKey(key)).get()?.toBigDecimal() ?: BigDecimal.ZERO
    }

    fun increment(key: String, amount: BigDecimal) {
        val bk = bucketKey(key)
        val lock = redissonClient.getLock("lock:$bk")
        lock.lock(5, TimeUnit.SECONDS)
        try {
            val bucket = redissonClient.getBucket<String>(bk)
            val current = bucket.get()?.toBigDecimal() ?: BigDecimal.ZERO
            bucket.set(current.add(amount).toPlainString())
            bucket.expire(Duration.ofDays(1))
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }
}
