package com.khope.product.event

sealed class ProductEvent(
    val productId: Long,
    val productName: String,
) {
    class Created(productId: Long, productName: String) : ProductEvent(productId, productName)
    class Updated(productId: Long, productName: String) : ProductEvent(productId, productName)
}
