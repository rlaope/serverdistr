package com.khope.order.controller

import com.khope.common.web.UserId
import com.khope.order.dto.OrderResponse
import com.khope.order.service.OrderService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
) {

    @PostMapping
    fun createOrder(@UserId userId: Long): ResponseEntity<OrderResponse> {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createOrderFromCart(userId))
    }

    @GetMapping
    fun getOrders(
        @UserId userId: Long,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<OrderResponse>> {
        return ResponseEntity.ok(orderService.findByUserId(userId, pageable))
    }

    @GetMapping("/{id}")
    fun getOrder(
        @UserId userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<OrderResponse> {
        return ResponseEntity.ok(orderService.findById(id, userId))
    }
}
