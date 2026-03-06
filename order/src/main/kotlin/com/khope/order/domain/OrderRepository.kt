package com.khope.order.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByUserId(userId: Long, pageable: Pageable): Page<Order>
    fun findByStatusAndCreatedAtBefore(status: OrderStatus, cutoff: LocalDateTime): List<Order>
}
