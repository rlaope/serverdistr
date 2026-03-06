package com.khope.product.event

import com.khope.product.service.StockService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class StockEventConsumer(
    private val stockService: StockService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["stock-decrease"], groupId = "product-service")
    fun onStockDecrease(message: String) {
        log.info("Received stock-decrease event: $message")
        val parts = message.split("|")
        if (parts.size != 2) return

        val productId = parts[0].toLongOrNull() ?: return
        val quantity = parts[1].toIntOrNull() ?: return

        stockService.decreaseStock(productId, quantity)
    }
}
