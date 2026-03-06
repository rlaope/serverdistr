package com.khope.order.event

import com.khope.order.client.CartClient
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val cartClient: CartClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderCompleted(event: OrderCompletedEvent) {
        event.items.forEach { item ->
            val message = "${item.productId}|${item.quantity}"
            log.info("Publishing stock-decrease event: $message")
            kafkaTemplate.send("stock-decrease", item.productId.toString(), message)
        }

        cartClient.clearCart(event.userId)
        log.info("Cart cleared for user: ${event.userId}")
    }
}
