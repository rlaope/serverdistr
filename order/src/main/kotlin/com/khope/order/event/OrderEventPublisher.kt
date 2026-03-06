package com.khope.order.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.khope.common.event.StockDecreaseRequest
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
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderCreated(event: OrderCreatedEvent) {
        log.info("Order committed, starting saga: orderId=${event.orderId}, items=${event.items.size}")

        sagaManager.initSaga(event.orderId, event.items.size)

        event.items.forEach { item ->
            val request = StockDecreaseRequest(
                orderId = event.orderId,
                productId = item.productId,
                quantity = item.quantity,
            )
            val json = objectMapper.writeValueAsString(request)
            kafkaTemplate.send("stock-decrease-request", item.productId.toString(), json)
        }
    }
}
