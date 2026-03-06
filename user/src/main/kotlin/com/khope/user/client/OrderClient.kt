package com.khope.user.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class OrderClient(
    @Value("\${service.order.url}") private val orderUrl: String,
) {
    private val webClient = WebClient.builder().baseUrl(orderUrl).build()

    fun getOrdersByUserId(userId: Long, page: Int = 0, size: Int = 20): OrderPageResponse? {
        return try {
            webClient.get()
                .uri("/api/orders?page={page}&size={size}", page, size)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<OrderPageResponse>() {})
                .block()
        } catch (e: Exception) {
            null
        }
    }
}

data class OrderSummary(
    val id: Long,
    val totalPrice: BigDecimal,
    val status: String,
    val createdAt: LocalDateTime,
    val items: List<OrderItemSummary>,
)

data class OrderItemSummary(
    val productId: Long,
    val productName: String,
    val price: BigDecimal,
    val quantity: Int,
    val subtotal: BigDecimal,
)

data class OrderPageResponse(
    val content: List<OrderSummary>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
)
