package com.khope.order.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.khope.common.event.StockDecreaseResult
import com.khope.common.event.StockRollbackRequest
import com.khope.order.domain.OrderRepository
import com.khope.order.service.OrderService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class OrderSagaManager(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun initSaga(orderId: Long, itemCount: Int) {
        val ops = redisTemplate.opsForValue()
        ops.set("saga:$orderId:total", itemCount.toString(), 10, TimeUnit.MINUTES)
        ops.set("saga:$orderId:processed", "0", 10, TimeUnit.MINUTES)
        ops.set("saga:$orderId:failed", "false", 10, TimeUnit.MINUTES)
    }

    @KafkaListener(topics = ["stock-decrease-result"], groupId = "order-service")
    fun onStockDecreaseResult(message: String) {
        val result = objectMapper.readValue(message, StockDecreaseResult::class.java)
        log.info("Received stock-decrease-result: orderId={}, productId={}, success={}", result.orderId, result.productId, result.success)

        val orderId = result.orderId

        if (result.success) {
            // 성공한 아이템 기록 (보상용)
            val itemJson = objectMapper.writeValueAsString(
                StockRollbackRequest(orderId, result.productId, result.quantity)
            )
            redisTemplate.opsForList().rightPush("saga:$orderId:items", itemJson)
        } else {
            redisTemplate.opsForValue().set("saga:$orderId:failed", "true", 10, TimeUnit.MINUTES)
        }

        val processed = redisTemplate.opsForValue().increment("saga:$orderId:processed") ?: 0
        val total = redisTemplate.opsForValue().get("saga:$orderId:total")?.toIntOrNull() ?: 0

        log.info("Saga progress: orderId=$orderId, processed=$processed/$total")

        if (processed.toInt() == total) {
            val hasFailed = redisTemplate.opsForValue().get("saga:$orderId:failed") == "true"
            if (hasFailed) {
                compensateOrder(orderId)
            } else {
                orderService.completeOrder(orderId)
                cleanupSaga(orderId)
            }
        }
    }

    fun compensateOrder(orderId: Long) {
        log.warn("Compensating order: $orderId")

        val successItems = redisTemplate.opsForList()
            .range("saga:$orderId:items", 0, -1) ?: emptyList()

        successItems.forEach { itemJson ->
            kafkaTemplate.send("stock-rollback", orderId.toString(), itemJson)
            log.info("Published stock-rollback: $itemJson")
        }

        orderService.failOrder(orderId, "Stock decrease failed - compensation executed")
        cleanupSaga(orderId)
    }

    private fun cleanupSaga(orderId: Long) {
        redisTemplate.delete("saga:$orderId:total")
        redisTemplate.delete("saga:$orderId:processed")
        redisTemplate.delete("saga:$orderId:failed")
        redisTemplate.delete("saga:$orderId:items")
    }
}
