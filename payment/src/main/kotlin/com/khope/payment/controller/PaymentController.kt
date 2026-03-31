package com.khope.payment.controller

import com.khope.payment.domain.PaymentStatus
import com.khope.payment.service.PaymentService
import com.khope.payment.util.Idempotent
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping(value = ["/api/payments"])
class PaymentController(
    private val paymentService: PaymentService
) {

    @Idempotent
    @PostMapping("/approve")
    fun approvePayment(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: ApproveRequest
    ): ResponseEntity<ApproveResponse> {
        val response = paymentService.approvePayment(idempotencyKey, request)
        return ResponseEntity.ok(response)
    }

    data class ApproveRequest(
        val orderId: String,
        val amount: BigDecimal,
        val paymentMethod: String,
    )

    data class ApproveResponse(
        val paymentId: Long,
        val orderId: String,
        val amount: BigDecimal,
        val status: PaymentStatus,
        val approvedAt: LocalDateTime,
        val message: String
    )
}