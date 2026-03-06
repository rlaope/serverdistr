package com.khope.product.service

import com.khope.product.domain.Product
import com.khope.product.domain.ProductRepository
import com.khope.product.domain.ProductStatus
import com.khope.product.dto.CreateProductRequest
import com.khope.product.dto.ProductResponse
import com.khope.product.dto.UpdateProductRequest
import com.khope.product.event.ProductEventPublisher
import org.springframework.cache.CacheManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val eventPublisher: ProductEventPublisher,
) {

    fun findAll(pageable: Pageable): Page<ProductResponse> {
        return productRepository.findByStatus(ProductStatus.ACTIVE, pageable)
            .map { it.toResponse() }
    }

    @Cacheable(cacheNames = ["products"], cacheManager = "tieredCacheManager", key = "#id")
    fun findById(id: Long): ProductResponse {
        val product = productRepository.findByIdOrNull(id)
            ?: throw IllegalArgumentException("Product not found: $id")
        return product.toResponse()

    }

    @Transactional
    fun create(request: CreateProductRequest): ProductResponse {
        val product = productRepository.save(
            Product(
                name = request.name,
                description = request.description,
                price = request.price,
                stock = request.stock,
            )
        )
        eventPublisher.publishCreated(product.id, product.name)
        return product.toResponse()
    }

    @Transactional
    @CacheEvict(cacheNames = ["products"], key = "#id", cacheManager = "tieredCacheManager")
    fun update(id: Long, request: UpdateProductRequest): ProductResponse {
        val product = productRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Product not found: $id") }

        product.name = request.name
        product.description = request.description
        product.price = request.price
        product.stock = request.stock
        product.updatedAt = LocalDateTime.now()

        val saved = productRepository.save(product)
        eventPublisher.publishUpdated(saved.id, saved.name)

        val response = saved.toResponse()

        return response
    }

    @Transactional
    @CacheEvict(cacheNames = ["products"], key = "#id", cacheManager = "tieredCacheManager")
    fun delete(id: Long) {
        val product = productRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Product not found: $id") }
        product.status = ProductStatus.INACTIVE
        productRepository.save(product)
    }

    private fun Product.toResponse() = ProductResponse(
        id = id,
        name = name,
        description = description,
        price = price,
        stock = stock,
        status = status.name,
        createdAt = createdAt,
    )
}
