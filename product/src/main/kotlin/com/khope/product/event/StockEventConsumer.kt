package com.khope.product.event

import com.khope.product.service.StockService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class StockEventConsumer(
    private val stockService: StockService,
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["stock-decrease-request"], groupId = "product-service")
    fun onStockDecreaseRequest(message: String) {
        log.info("Received stock-decrease-request: $message")
        val parts = message.split("|")
        if (parts.size != 3) return

        val orderId = parts[0].toLongOrNull() ?: return
        val productId = parts[1].toLongOrNull() ?: return
        val quantity = parts[2].toIntOrNull() ?: return

        val resultMessage = "$orderId|$productId|$quantity"

        try {
            val success = stockService.decreaseStock(productId, quantity)
            val resultTopic = if (success) "stock-decrease-success" else "stock-decrease-failed"
            kafkaTemplate.send(resultTopic, orderId.toString(), resultMessage)
            log.info("Published $resultTopic: $resultMessage")
        } catch (e: Exception) {
            // 예상치 못한 예외 → 실패로 응답하여 Saga가 멈추지 않도록
            log.error("Unexpected error during stock decrease, sending failure response", e)
            kafkaTemplate.send("stock-decrease-failed", orderId.toString(), resultMessage)
        }
    }

    @KafkaListener(topics = ["stock-rollback"], groupId = "product-service")
    fun onStockRollback(message: String) {
        log.info("Received stock-rollback: $message")
        val parts = message.split("|")
        if (parts.size != 3) return

        val productId = parts[1].toLongOrNull() ?: return
        val quantity = parts[2].toIntOrNull() ?: return

        try {
            stockService.restoreStock(productId, quantity)
        } catch (e: Exception) {
            // 보상 실패는 로그로 남기고 수동 처리 대상으로 기록
            log.error("CRITICAL: Stock rollback failed! Manual intervention required. productId=$productId, quantity=$quantity", e)
        }
    }
}
