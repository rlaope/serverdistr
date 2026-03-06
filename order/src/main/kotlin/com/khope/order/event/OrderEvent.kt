package com.khope.order.event

data class StockDecreaseItem(
    val productId: Long,
    val quantity: Int,
)

// Order → Product: 재고 차감 요청 (트랜잭션 커밋 후 Kafka 발행)
data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<StockDecreaseItem>,
)
