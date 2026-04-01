package com.khope.payment.domain.payment

import com.khope.payment.domain.merchant.Merchant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class Payment(
    @ManyToOne(fetch = FetchType.LAZY)
    val merchant: Merchant,

    @Column(nullable = false, unique = true)
    val orderId: String,

    @Column(columnDefinition = "DECIMAL(18, 0)")
    val amount: BigDecimal,

    @Column
    @Enumerated(EnumType.STRING)
    val status: PaymentStatus,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = true, columnDefinition = "DATETIME")
    var paidDate: LocalDate? = null

    @Column(nullable = true, columnDefinition = "DATETIME")
    var canceledDate: LocalDateTime? = null
}

enum class PaymentStatus {
    COMPLETED, CANCELLED, REFUNDED, APPROVED
}
