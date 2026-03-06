package com.khope.order.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class CreateOrderRequest(
    val cartItems: Map<Long, Int>, // productId -> quantity
)

data class OrderResponse(
    val id: Long,
    val userId: Long,
    val totalPrice: BigDecimal,
    val status: String,
    val createdAt: LocalDateTime,
    val items: List<OrderItemResponse>,
)

data class OrderItemResponse(
    val productId: Long,
    val productName: String,
    val price: BigDecimal,
    val quantity: Int,
    val subtotal: BigDecimal,
)
