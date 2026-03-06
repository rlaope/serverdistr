package com.khope.order.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@Component
class CartClient(
    @Value("\${service.product.url}") private val productUrl: String,
) {
    private val webClient = WebClient.builder().baseUrl(productUrl).build()

    fun getCart(userId: Long): CartInfo? {
        return try {
            webClient.get()
                .uri("/api/cart")
                .header("X-User-Id", userId.toString())
                .retrieve()
                .bodyToMono(CartInfo::class.java)
                .block()
        } catch (e: Exception) {
            null
        }
    }

    fun clearCart(userId: Long) {
        try {
            webClient.delete()
                .uri("/api/cart")
                .header("X-User-Id", userId.toString())
                .retrieve()
                .toBodilessEntity()
                .block()
        } catch (_: Exception) {
        }
    }
}

data class CartInfo(
    val userId: Long,
    val items: List<CartItemInfo>,
    val totalPrice: BigDecimal,
)

data class CartItemInfo(
    val productId: Long,
    val productName: String,
    val price: BigDecimal,
    val quantity: Int,
    val subtotal: BigDecimal,
)
