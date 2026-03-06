package com.khope.product.cart.controller

import com.khope.common.web.UserId
import com.khope.product.cart.dto.AddCartItemRequest
import com.khope.product.cart.dto.CartResponse
import com.khope.product.cart.dto.UpdateCartItemRequest
import com.khope.product.cart.service.CartService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/cart")
class CartController(
    private val cartService: CartService,
) {

    @GetMapping
    fun getCart(@UserId userId: Long): ResponseEntity<CartResponse> {
        return ResponseEntity.ok(cartService.getCart(userId))
    }

    @PostMapping("/items")
    fun addItem(
        @UserId userId: Long,
        @Valid @RequestBody request: AddCartItemRequest,
    ): ResponseEntity<Void> {
        cartService.addItem(userId, request.productId, request.quantity)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/items/{productId}")
    fun updateItem(
        @UserId userId: Long,
        @PathVariable productId: Long,
        @Valid @RequestBody request: UpdateCartItemRequest,
    ): ResponseEntity<Void> {
        cartService.updateQuantity(userId, productId, request.quantity)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/items/{productId}")
    fun removeItem(
        @UserId userId: Long,
        @PathVariable productId: Long,
    ): ResponseEntity<Void> {
        cartService.removeItem(userId, productId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    fun clearCart(@UserId userId: Long): ResponseEntity<Void> {
        cartService.clearCart(userId)
        return ResponseEntity.noContent().build()
    }
}
