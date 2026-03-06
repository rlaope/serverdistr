package com.khope.order.service

import com.khope.common.exception.AccessDeniedException
import com.khope.common.exception.ErrorCode
import com.khope.common.exception.NotFoundException
import com.khope.common.exception.ValidationException
import com.khope.order.client.CartClient
import com.khope.order.domain.Order
import com.khope.order.domain.OrderItem
import com.khope.order.domain.OrderRepository
import com.khope.order.domain.OrderStatus
import com.khope.order.dto.OrderItemResponse
import com.khope.order.dto.OrderResponse
import com.khope.order.event.OrderCreatedEvent
import com.khope.order.event.StockDecreaseItem
import com.khope.order.validator.OrderValidator
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val cartClient: CartClient,
    private val orderValidator: OrderValidator,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createOrderFromCart(userId: Long): OrderResponse {
        val cart = cartClient.getCart(userId)
            ?: throw ValidationException(ErrorCode.ORDER_FAILED, "Failed to fetch cart")

        orderValidator.validateCart(cart)

        val order = Order(
            userId = userId,
            totalPrice = cart.totalPrice,
            status = OrderStatus.PENDING,
        )

        cart.items.forEach { item ->
            order.items.add(
                OrderItem(
                    order = order,
                    productId = item.productId,
                    productName = item.productName,
                    price = item.price,
                    quantity = item.quantity,
                    subtotal = item.subtotal,
                )
            )
        }

        val saved = orderRepository.save(order)
        log.info("Order created with PENDING status: orderId=${saved.id}")

        // 트랜잭션 커밋 후 Kafka로 재고 차감 요청
        eventPublisher.publishEvent(
            OrderCreatedEvent(
                orderId = saved.id,
                userId = userId,
                items = cart.items.map { StockDecreaseItem(it.productId, it.quantity) },
            )
        )

        return saved.toResponse()
    }

    @Transactional
    fun completeOrder(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException(ErrorCode.ORDER_NOT_FOUND) }

        if (order.status != OrderStatus.PENDING) return

        order.status = OrderStatus.PAID
        orderRepository.save(order)

        // 장바구니 비우기
        cartClient.clearCart(order.userId)
        log.info("Order completed: orderId=$orderId")
    }

    @Transactional
    fun failOrder(orderId: Long, reason: String) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException(ErrorCode.ORDER_NOT_FOUND) }

        if (order.status != OrderStatus.PENDING) return

        order.status = OrderStatus.FAILED
        orderRepository.save(order)
        log.warn("Order failed: orderId=$orderId, reason=$reason")
    }

    fun findByUserId(userId: Long, pageable: Pageable): Page<OrderResponse> {
        return orderRepository.findByUserId(userId, pageable)
            .map { it.toResponse() }
    }

    fun findById(orderId: Long, userId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException(ErrorCode.ORDER_NOT_FOUND, "Order not found: $orderId") }

        if (order.userId != userId) {
            throw AccessDeniedException()
        }
        return order.toResponse()
    }

    private fun Order.toResponse() = OrderResponse(
        id = id,
        userId = userId,
        totalPrice = totalPrice,
        status = status.name,
        createdAt = createdAt,
        items = items.map { it.toResponse() },
    )

    private fun OrderItem.toResponse() = OrderItemResponse(
        productId = productId,
        productName = productName,
        price = price,
        quantity = quantity,
        subtotal = subtotal,
    )
}
