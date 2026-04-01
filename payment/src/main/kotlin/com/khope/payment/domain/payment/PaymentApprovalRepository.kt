package com.khope.payment.domain.payment

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentApprovalRepository : JpaRepository<PaymentApproval, Long> {
    fun findByPayment(payment: Payment): PaymentApproval?
    fun findByIdempotencyKey(idempotencyKey: String): PaymentApproval?
}