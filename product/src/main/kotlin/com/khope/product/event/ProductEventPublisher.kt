package com.khope.product.event

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductCreated(event: ProductEvent.Created) {
        val message = "${event.productId}|${event.productName}"
        log.info("Publishing product-created event: $message")
        kafkaTemplate.send("product-created", event.productId.toString(), message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductUpdated(event: ProductEvent.Updated) {
        val message = "${event.productId}|${event.productName}"
        log.info("Publishing product-updated event: $message")
        kafkaTemplate.send("product-updated", event.productId.toString(), message)
    }
}
