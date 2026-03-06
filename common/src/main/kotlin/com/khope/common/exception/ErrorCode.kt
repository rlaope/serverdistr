package com.khope.common.exception

enum class ErrorCode(
    val status: Int,
    val message: String,
) {
    // Common
    INVALID_INPUT(400, "Invalid input"),
    ACCESS_DENIED(403, "Access denied"),
    INTERNAL_ERROR(500, "Internal server error"),

    // Auth
    EMAIL_ALREADY_EXISTS(409, "Email already exists"),
    INVALID_CREDENTIALS(401, "Invalid email or password"),
    INVALID_TOKEN(401, "Invalid token"),

    // User
    USER_NOT_FOUND(404, "User not found"),

    // Product
    PRODUCT_NOT_FOUND(404, "Product not found"),
    PRODUCT_NOT_AVAILABLE(400, "Product not available"),
    INSUFFICIENT_STOCK(400, "Insufficient stock"),

    // Cart
    CART_ITEM_NOT_FOUND(404, "Item not in cart"),
    CART_EMPTY(400, "Cart is empty"),

    // Order
    ORDER_NOT_FOUND(404, "Order not found"),
    ORDER_FAILED(500, "Order creation failed"),
}
