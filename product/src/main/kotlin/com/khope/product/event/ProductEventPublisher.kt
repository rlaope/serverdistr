package com.khope.product.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.khope.common.event.ProductCreatedEvent
import com.khope.common.event.ProductUpdatedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductCreated(event: ProductEvent.Created) {
        val json = objectMapper.writeValueAsString(
            ProductCreatedEvent(productId = event.productId, productName = event.productName)
        )
        log.info("Publishing product-created event: $json")
        kafkaTemplate.send("product-created", event.productId.toString(), json)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductUpdated(event: ProductEvent.Updated) {
        val json = objectMapper.writeValueAsString(
            ProductUpdatedEvent(productId = event.productId, productName = event.productName)
        )
        log.info("Publishing product-updated event: $json")
        kafkaTemplate.send("product-updated", event.productId.toString(), json)
    }
}
