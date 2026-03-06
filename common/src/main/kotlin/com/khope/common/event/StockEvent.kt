package com.khope.common.event

data class StockDecreaseRequest(
    val orderId: Long,
    val productId: Long,
    val quantity: Int,
)

data class StockDecreaseResult(
    val orderId: Long,
    val productId: Long,
    val quantity: Int,
    val success: Boolean,
    val reason: String? = null,
)

data class StockRollbackRequest(
    val orderId: Long,
    val productId: Long,
    val quantity: Int,
)
