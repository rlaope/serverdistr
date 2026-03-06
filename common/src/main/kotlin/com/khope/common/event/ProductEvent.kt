package com.khope.common.event

data class ProductCreatedEvent(
    val productId: Long,
    val productName: String,
)

data class ProductUpdatedEvent(
    val productId: Long,
    val productName: String,
)

data class UserSignupEvent(
    val userId: Long,
    val email: String,
)
