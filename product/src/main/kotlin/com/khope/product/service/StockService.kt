package com.khope.product.service

import com.khope.common.exception.ErrorCode
import com.khope.common.exception.NotFoundException
import com.khope.product.domain.ProductRepository
import com.khope.product.domain.ProductStatus
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StockService(
    private val productRepository: ProductRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @CacheEvict(cacheNames = ["products"], key = "#productId")
    @Transactional
    fun decreaseStock(productId: Long, quantity: Int) {
        val product = productRepository.findById(productId)
            .orElseThrow { NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: $productId") }

        product.stock = (product.stock - quantity).coerceAtLeast(0)
        if (product.stock == 0) {
            product.status = ProductStatus.SOLD_OUT
        }

        productRepository.save(product)
        log.info("Stock decreased: product=$productId, remaining=${product.stock}")
    }
}
