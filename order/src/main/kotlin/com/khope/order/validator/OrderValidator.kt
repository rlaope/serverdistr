package com.khope.order.validator

import com.khope.common.exception.ErrorCode
import com.khope.common.exception.NotFoundException
import com.khope.common.exception.ValidationException
import com.khope.order.client.CartInfo
import com.khope.order.client.CartItemInfo
import com.khope.order.client.ProductClient
import com.khope.order.client.ProductInfo
import org.springframework.stereotype.Component

@Component
class OrderValidator(
    private val productClient: ProductClient,
) {

    fun validateCart(cart: CartInfo): List<ProductInfo> {
        if (cart.items.isEmpty()) {
            throw ValidationException(ErrorCode.CART_EMPTY)
        }

        return cart.items.map { item -> validateCartItem(item) }
    }

    private fun validateCartItem(item: CartItemInfo): ProductInfo {
        val product = productClient.getProduct(item.productId)
            ?: throw NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: ${item.productId}")

        if (product.status != "ACTIVE") {
            throw ValidationException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Product not available: ${product.name}")
        }

        if (product.stock < item.quantity) {
            throw ValidationException(
                ErrorCode.INSUFFICIENT_STOCK,
                "Insufficient stock for ${product.name}: available=${product.stock}, requested=${item.quantity}"
            )
        }

        return product
    }
}
