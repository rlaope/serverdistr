package com.khope.payment.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.khope.payment.exception.GlobalHttpException
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class IdempotentManager(
    private val redissonClient: RedissonClient,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private const val PREFIX = "idempotent"
        private val PROCESSING_TTL = Duration.ofSeconds(30)
        private val COMPLETED_TTL = Duration.ofHours(24)
    }

    fun tryLock(key: String): RLock {
        val lock = redissonClient.getLock("lock:$PREFIX:$key")
        val acquired = lock.tryLock(5, 30, TimeUnit.SECONDS)
        if (!acquired) {
            throw GlobalHttpException(409, "Payment is currently being processed with this idempotency key")
        }
        return lock
    }

    fun getStatus(key: String): String? {
        return redissonClient.getBucket<String>("$PREFIX:$key:status").get()
    }

    fun getRequestHash(key: String): String? {
        return redissonClient.getBucket<String>("$PREFIX:$key:request_hash").get()
    }

    fun getCachedResponse(key: String): String? {
        return redissonClient.getBucket<String>("$PREFIX:$key:response").get()
    }

    fun markProcessing(key: String, requestHash: String) {
        val statusBucket = redissonClient.getBucket<String>("$PREFIX:$key:status")
        statusBucket.set("PROCESSING")
        statusBucket.expire(PROCESSING_TTL)

        val hashBucket = redissonClient.getBucket<String>("$PREFIX:$key:request_hash")
        hashBucket.set(requestHash)
        hashBucket.expire(COMPLETED_TTL)
    }

    fun markCompleted(key: String, status: String, responseJson: String) {
        val statusBucket = redissonClient.getBucket<String>("$PREFIX:$key:status")
        statusBucket.set(status)
        statusBucket.expire(COMPLETED_TTL)

        val responseBucket = redissonClient.getBucket<String>("$PREFIX:$key:response")
        responseBucket.set(responseJson)
        responseBucket.expire(COMPLETED_TTL)
    }

    fun cleanup(key: String) {
        redissonClient.getBucket<String>("$PREFIX:$key:status").delete()
        redissonClient.getBucket<String>("$PREFIX:$key:request_hash").delete()
        redissonClient.getBucket<String>("$PREFIX:$key:response").delete()
    }

    fun hashRequest(body: Any): String {
        val json = objectMapper.writeValueAsString(body)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(json.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
