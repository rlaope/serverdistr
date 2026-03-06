package com.khope.product.cart.service

import com.khope.common.exception.ErrorCode
import com.khope.common.exception.NotFoundException
import com.khope.common.exception.ValidationException
import com.khope.product.cart.dto.CartItemResponse
import com.khope.product.cart.dto.CartResponse
import com.khope.product.domain.ProductRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class CartService(
    private val redisTemplate: StringRedisTemplate,
    private val productRepository: ProductRepository,
) {
    private fun cartKey(userId: Long) = "cart:$userId"

    fun getCart(userId: Long): CartResponse {
        val entries = redisTemplate.opsForHash<String, String>()
            .entries(cartKey(userId))

        val items = entries.mapNotNull { (productIdStr, quantityStr) ->
            val productId = productIdStr.toLongOrNull() ?: return@mapNotNull null
            val quantity = quantityStr.toIntOrNull() ?: return@mapNotNull null
            val product = productRepository.findByIdOrNull(productId) ?: return@mapNotNull null

            CartItemResponse(
                productId = product.id,
                productName = product.name,
                price = product.price,
                quantity = quantity,
                subtotal = product.price.multiply(BigDecimal(quantity)),
            )
        }

        return CartResponse(
            userId = userId,
            items = items,
            totalPrice = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotal) },
        )
    }

    fun addItem(userId: Long, productId: Long, quantity: Int) {
        productRepository.findByIdOrNull(productId)
            ?: throw NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: $productId")

        val ops = redisTemplate.opsForHash<String, String>()
        val current = ops.get(cartKey(userId), productId.toString())?.toIntOrNull() ?: 0
        ops.put(cartKey(userId), productId.toString(), (current + quantity).toString())
    }

    fun updateQuantity(userId: Long, productId: Long, quantity: Int) {
        val ops = redisTemplate.opsForHash<String, String>()
        if (!ops.hasKey(cartKey(userId), productId.toString())) {
            throw ValidationException(ErrorCode.CART_ITEM_NOT_FOUND)
        }
        ops.put(cartKey(userId), productId.toString(), quantity.toString())
    }

    fun removeItem(userId: Long, productId: Long) {
        redisTemplate.opsForHash<String, String>().delete(cartKey(userId), productId.toString())
    }

    fun clearCart(userId: Long) {
        redisTemplate.delete(cartKey(userId))
    }
}
