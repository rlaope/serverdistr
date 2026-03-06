package com.khope.product.cart.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class AddCartItemRequest(
    @field:NotNull
    val productId: Long,
    @field:Min(1)
    val quantity: Int = 1,
)

data class UpdateCartItemRequest(
    @field:Min(1)
    val quantity: Int,
)

data class CartResponse(
    val userId: Long,
    val items: List<CartItemResponse>,
    val totalPrice: java.math.BigDecimal,
)

data class CartItemResponse(
    val productId: Long,
    val productName: String,
    val price: java.math.BigDecimal,
    val quantity: Int,
    val subtotal: java.math.BigDecimal,
)
