package com.khope.order.saga

import com.khope.order.domain.OrderRepository
import com.khope.order.domain.OrderStatus
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Redis keys for saga tracking
    // saga:{orderId}:total    = 전체 아이템 수
    // saga:{orderId}:success  = 성공 카운트
    // saga:{orderId}:failed   = 실패 여부
    // saga:{orderId}:items    = 성공한 아이템들 (productId|quantity)

    fun initSaga(orderId: Long, itemCount: Int) {
        val ops = redisTemplate.opsForValue()
        ops.set("saga:$orderId:total", itemCount.toString(), 10, TimeUnit.MINUTES)
        ops.set("saga:$orderId:success", "0", 10, TimeUnit.MINUTES)
        ops.set("saga:$orderId:failed", "false", 10, TimeUnit.MINUTES)
    }

    // 재고 차감 성공 응답: orderId|productId|quantity
    @KafkaListener(topics = ["stock-decrease-success"], groupId = "order-service")
    fun onStockDecreaseSuccess(message: String) {
        log.info("Received stock-decrease-success: $message")
        val parts = message.split("|")
        if (parts.size != 3) return

        val orderId = parts[0].toLongOrNull() ?: return
        val productId = parts[1]
        val quantity = parts[2]

        // 성공 아이템 기록
        redisTemplate.opsForList().rightPush("saga:$orderId:items", "$productId|$quantity")
        val successCount = redisTemplate.opsForValue().increment("saga:$orderId:success") ?: 0
        val total = redisTemplate.opsForValue().get("saga:$orderId:total")?.toIntOrNull() ?: 0

        log.info("Saga progress: orderId=$orderId, success=$successCount/$total")

        if (successCount.toInt() == total) {
            val hasFailed = redisTemplate.opsForValue().get("saga:$orderId:failed") == "true"
            if (hasFailed) {
                compensateOrder(orderId)
            } else {
                orderService.completeOrder(orderId)
                cleanupSaga(orderId)
            }
        }
    }

    // 재고 차감 실패 응답: orderId|productId|quantity
    @KafkaListener(topics = ["stock-decrease-failed"], groupId = "order-service")
    fun onStockDecreaseFailed(message: String) {
        log.warn("Received stock-decrease-failed: $message")
        val parts = message.split("|")
        if (parts.size != 3) return

        val orderId = parts[0].toLongOrNull() ?: return

        // 실패 플래그 설정
        redisTemplate.opsForValue().set("saga:$orderId:failed", "true", 10, TimeUnit.MINUTES)
        val successCount = redisTemplate.opsForValue().increment("saga:$orderId:success") ?: 0
        val total = redisTemplate.opsForValue().get("saga:$orderId:total")?.toIntOrNull() ?: 0

        log.info("Saga progress (with failure): orderId=$orderId, processed=$successCount/$total")

        // 모든 응답이 돌아왔으면 보상 트랜잭션
        if (successCount.toInt() == total) {
            compensateOrder(orderId)
        }
    }

    private fun compensateOrder(orderId: Long) {
        log.warn("Compensating order: $orderId")

        // 이미 성공한 재고 차감을 롤백
        val successItems = redisTemplate.opsForList()
            .range("saga:$orderId:items", 0, -1) ?: emptyList()

        successItems.forEach { item ->
            val rollbackMessage = "$orderId|$item"
            log.info("Publishing stock-rollback: $rollbackMessage")
            kafkaTemplate.send("stock-rollback", orderId.toString(), rollbackMessage)
        }

        orderService.failOrder(orderId, "Stock decrease failed - compensation executed")
        cleanupSaga(orderId)
    }

    private fun cleanupSaga(orderId: Long) {
        redisTemplate.delete("saga:$orderId:total")
        redisTemplate.delete("saga:$orderId:success")
        redisTemplate.delete("saga:$orderId:failed")
        redisTemplate.delete("saga:$orderId:items")
    }
}
