package com.khope.product.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.khope.common.event.StockDecreaseRequest
import com.khope.common.event.StockDecreaseResult
import com.khope.common.event.StockRollbackRequest
import com.khope.common.kafka.SagaConsumerTemplate
import com.khope.product.service.StockService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class StockEventConsumer(
    private val stockService: StockService,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sagaConsumer by lazy { SagaConsumerTemplate(kafkaTemplate, objectMapper) }

    @KafkaListener(topics = ["stock-decrease-request"], groupId = "product-service")
    fun onStockDecreaseRequest(message: String) {
        log.info("Received stock-decrease-request: $message")

        sagaConsumer.execute(
            message = message,
            type = StockDecreaseRequest::class.java,
            resultTopic = "stock-decrease-result",
            fallback = { raw, _ ->
                try {
                    val req = objectMapper.readValue(raw, StockDecreaseRequest::class.java)
                    StockDecreaseResult(req.orderId, req.productId, req.quantity, false, "Processing error")
                } catch (_: Exception) {
                    StockDecreaseResult(0, 0, 0, false, "Invalid message")
                }
            },
        ) { request ->
            val success = stockService.decreaseStock(request.productId, request.quantity)

            StockDecreaseResult(
                orderId = request.orderId,
                productId = request.productId,
                quantity = request.quantity,
                success = success,
                reason = if (!success) "Insufficient stock or product unavailable" else null,
            )
        }
    }

    @KafkaListener(topics = ["stock-rollback"], groupId = "product-service")
    fun onStockRollback(message: String) {
        log.info("Received stock-rollback: $message")
        val request = objectMapper.readValue(message, StockRollbackRequest::class.java)
        stockService.restoreStock(request.productId, request.quantity)
    }
}
