package com.khope.product.controller

import com.khope.product.dto.CreateProductRequest
import com.khope.product.dto.ProductResponse
import com.khope.product.dto.UpdateProductRequest
import com.khope.product.service.ProductService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productService: ProductService,
) {

    @GetMapping
    fun getProducts(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Page<ProductResponse>> {
        return ResponseEntity.ok(productService.findAll(pageable))
    }

    @GetMapping("/{id}")
    fun getProduct(@PathVariable id: Long): ResponseEntity<ProductResponse> {
        return ResponseEntity.ok(productService.findById(id))
    }

    @PostMapping
    fun createProduct(@Valid @RequestBody request: CreateProductRequest): ResponseEntity<ProductResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request))
    }

    @PutMapping("/{id}")
    fun updateProduct(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateProductRequest,
    ): ResponseEntity<ProductResponse> {
        return ResponseEntity.ok(productService.update(id, request))
    }

    @DeleteMapping("/{id}")
    fun deleteProduct(@PathVariable id: Long): ResponseEntity<Void> {
        productService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
