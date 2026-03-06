package com.khope.order.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@Component
class ProductClient(
    @Value("\${service.product.url}") private val productUrl: String,
) {
    private val webClient = WebClient.builder().baseUrl(productUrl).build()

    fun getProduct(productId: Long): ProductInfo? {
        return try {
            webClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .bodyToMono(ProductInfo::class.java)
                .block()
        } catch (e: Exception) {
            null
        }
    }
}

data class ProductInfo(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val stock: Int,
    val status: String,
)
