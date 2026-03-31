package com.khope.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment_approval")
class PaymentApproval (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    val payment: Payment,

    @Column(nullable = false, unique = true)
    val idempotencyKey: String,

    val orderId: String,

    @Column(nullable = false, columnDefinition = "DECIMAL(18, 0)")
    val amount: BigDecimal,

    val status: PaymentStatus,

    @Column(nullable = true)
    val failureReason: String? = null,

    @Column(nullable = true, columnDefinition = "DATETIME")
    val approvedAt: LocalDateTime? = null,

    @Column(nullable = true, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    val createdAt: LocalDateTime? = null,
)