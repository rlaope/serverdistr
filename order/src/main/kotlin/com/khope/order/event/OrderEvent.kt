package com.khope.order.event

data class StockDecreaseEvent(
    val productId: Long,
    val quantity: Int,
)

data class OrderCompletedEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<StockDecreaseEvent>,
)
