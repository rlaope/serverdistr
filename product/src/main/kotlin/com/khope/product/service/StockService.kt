package com.khope.product.service

import com.khope.product.domain.ProductRepository
import com.khope.product.domain.ProductStatus
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StockService(
    private val productRepository: ProductRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @CacheEvict(cacheNames = ["products"], key = "#productId")
    @Transactional
    fun decreaseStock(productId: Long, quantity: Int): Boolean {
        val product = productRepository.findByIdOrNull(productId) ?: run {
            log.error("Stock decrease failed: product not found $productId")
            return false
        }

        if (product.status != ProductStatus.ACTIVE || product.stock < quantity) {
            log.warn("Stock decrease failed: product=$productId, available=${product.stock}, requested=$quantity")
            return false
        }

        product.stock -= quantity
        if (product.stock == 0) {
            product.status = ProductStatus.SOLD_OUT
        }

        productRepository.save(product)
        log.info("Stock decreased: product=$productId, remaining=${product.stock}")
        return true
    }

    @CacheEvict(cacheNames = ["products"], key = "#productId")
    @Transactional
    fun restoreStock(productId: Long, quantity: Int) {
        val product = productRepository.findByIdOrNull(productId) ?: run {
            log.error("Stock restore failed: product not found $productId")
            return
        }

        product.stock += quantity
        if (product.status == ProductStatus.SOLD_OUT) {
            product.status = ProductStatus.ACTIVE
        }

        productRepository.save(product)
        log.info("Stock restored (compensation): product=$productId, restored=$quantity, current=${product.stock}")
    }
}
