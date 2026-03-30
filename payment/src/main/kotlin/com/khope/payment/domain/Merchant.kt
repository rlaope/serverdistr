package com.khope.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "merchant")
class Merchant(
    @Column(name = "name", nullable = false, unique = true)
    val name: String,

    @Column(name = "business_number", nullable = false, unique = true)
    val businessNumber: String,

    @Column(name = "fee_rate", columnDefinition = "DECIMAL(10, 3)")
    val feeRate: BigDecimal,

    @Column
    @Enumerated(EnumType.STRING)
    val status: MerchantStatus,

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}

enum class MerchantStatus {
    ACTIVE,
    SUSPENDED;
}
