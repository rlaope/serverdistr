package com.khope.product.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long> {
    fun findByStatus(status: ProductStatus, pageable: Pageable): Page<Product>
}
