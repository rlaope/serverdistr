package com.khope.product.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Column(length = 2000)
    var description: String? = null,

    @Column(nullable = false)
    var price: BigDecimal,

    @Column(nullable = false)
    var stock: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus = ProductStatus.ACTIVE,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    var updatedAt: LocalDateTime = LocalDateTime.now(),
)

enum class ProductStatus {
    ACTIVE, INACTIVE, SOLD_OUT
}
