package com.khope.product.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreateProductRequest(
    @field:NotBlank
    val name: String,
    val description: String? = null,
    @field:NotNull
    @field:Min(0)
    val price: BigDecimal,
    @field:Min(0)
    val stock: Int = 0,
)

data class UpdateProductRequest(
    @field:NotBlank
    val name: String,
    val description: String? = null,
    @field:NotNull
    @field:Min(0)
    val price: BigDecimal,
    @field:Min(0)
    val stock: Int = 0,
)

data class ProductResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val stock: Int,
    val status: String,
    val createdAt: LocalDateTime,
)
