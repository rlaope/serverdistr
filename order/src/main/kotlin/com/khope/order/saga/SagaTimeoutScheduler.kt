package com.khope.order.saga

import com.khope.order.domain.OrderRepository
import com.khope.order.domain.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class SagaTimeoutScheduler(
    private val orderRepository: OrderRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 5분 이상 PENDING인 주문을 FAILED 처리
    @Scheduled(fixedDelay = 60_000) // 1분마다 체크
    @Transactional
    fun timeoutPendingOrders() {
        val cutoff = LocalDateTime.now().minusMinutes(5)
        val pendingOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff)

        pendingOrders.forEach { order ->
            order.status = OrderStatus.FAILED
            orderRepository.save(order)
            log.warn("Order timed out (saga incomplete): orderId=${order.id}, created=${order.createdAt}")
        }

        if (pendingOrders.isNotEmpty()) {
            log.warn("Timed out ${pendingOrders.size} pending orders")
        }
    }
}
