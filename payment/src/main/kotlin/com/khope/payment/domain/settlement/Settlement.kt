package com.khope.payment.domain.settlement

import com.khope.payment.domain.merchant.Merchant
import com.khope.payment.domain.payment.Payment
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_merchant_settlement_date",
            columnNames = ["merchant_id", "settlement_date"]
        )
    ]
)
class Settlement(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    val merchant: Merchant,

    @Column(name = "settlement_date", nullable = false, columnDefinition = "DATE")
    val settlementDate: LocalDate,

    @Column(nullable = false, columnDefinition = "INT")
    var paymentCount: Int,

    @Column(nullable = false, columnDefinition = "DECIMAL(18, 2)")
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, columnDefinition = "DECIMAL(18, 2)")
    var totalFee: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, columnDefinition = "DECIMAL(18, 2)")
    var netAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: SettlementStatus,

    @Column(nullable = false, columnDefinition = "DATETIME default CURRENT_TIMESTAMP")
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun confirm(): Settlement {
        this.status = SettlementStatus.CONFIRMED
        return this
    }

    fun addPayments(payments: List<Payment>) {
        val addedAmount = payments.sumOf { it.amount }
        val addedFee = payments.sumOf { it.amount.multiply(merchant.feeRate).setScale(0, RoundingMode.DOWN) }

        this.totalAmount += addedAmount
        this.totalFee += addedFee
        this.netAmount = this.totalAmount.subtract(this.totalFee)
        this.paymentCount += payments.size
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    val settlementId: Long = 0

    companion object {
        fun createPending(
            merchant: Merchant,
            date: LocalDate,
            paymentList: List<Payment>
        ) : Settlement{
            val totalAmount = paymentList.sumOf { it.amount }
            val totalFee = paymentList.sumOf { it.amount.multiply(merchant.feeRate).setScale(0, RoundingMode.DOWN) }
            val netAmount = totalAmount.subtract(totalFee)

            return Settlement(
                merchant = merchant,
                settlementDate = date,
                paymentCount = paymentList.size,
                totalAmount = totalAmount,
                totalFee = totalFee,
                netAmount = netAmount,
                status = SettlementStatus.PENDING
            )
        }
    }
}

enum class SettlementStatus {
    PENDING, CONFIRMED, PAID
}
