package com.khope.order.event

import com.khope.order.saga.OrderSagaManager
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val sagaManager: OrderSagaManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderCreated(event: OrderCreatedEvent) {
        log.info("Order committed, starting saga: orderId=${event.orderId}, items=${event.items.size}")

        // Saga 상태 초기화 (Redis)
        sagaManager.initSaga(event.orderId, event.items.size)

        // 각 아이템에 대해 재고 차감 요청
        event.items.forEach { item ->
            val message = "${event.orderId}|${item.productId}|${item.quantity}"
            kafkaTemplate.send("stock-decrease-request", item.productId.toString(), message)
        }
    }
}
