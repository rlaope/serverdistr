package com.khope.product.event

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ProductEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishCreated(productId: Long, productName: String) {
        val message = "$productId|$productName"
        log.info("Publishing product-created event: $message")
        kafkaTemplate.send("product-created", productId.toString(), message)
    }

    fun publishUpdated(productId: Long, productName: String) {
        val message = "$productId|$productName"
        log.info("Publishing product-updated event: $message")
        kafkaTemplate.send("product-updated", productId.toString(), message)
    }
}
