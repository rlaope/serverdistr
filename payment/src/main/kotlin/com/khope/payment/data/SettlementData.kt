package com.khope.payment.data

import com.khope.payment.domain.settlement.Settlement
import com.khope.payment.domain.settlement.SettlementStatus
import java.math.BigDecimal
import java.time.LocalDate

data class SettlementData(
    val id: Long,
    val settlementDate: LocalDate,
    val totalAmount: BigDecimal,
    val totalFee: BigDecimal,
    val netAmount: BigDecimal,
    val paymentCount: Int,
    val status: SettlementStatus
) {
    companion object {
        fun toData(settlement: Settlement): SettlementData {
            return SettlementData(
                settlement.settlementId,
                settlementDate = settlement.settlementDate,
                totalAmount = settlement.totalAmount,
                totalFee = settlement.totalFee,
                netAmount = settlement.netAmount,
                paymentCount = settlement.paymentCount,
                status = settlement.status
            )
        }
    }
}
