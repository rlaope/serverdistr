package com.khope.payment.service

import com.khope.payment.controller.SettlementController.SettlementResponse
import com.khope.payment.data.SettlementData
import com.khope.payment.domain.merchant.MerchantRepository
import com.khope.payment.domain.payment.PaymentRepository
import com.khope.payment.domain.settlement.Settlement
import com.khope.payment.domain.settlement.SettlementRepository
import com.khope.payment.domain.settlement.SettlementStatus
import com.khope.payment.exception.GlobalHttpException
import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@Service
class SettlementService(
    private val merchantRepository: MerchantRepository,
    private val settlementRepository: SettlementRepository,
    private val paymentRepository: PaymentRepository
) {

    @Transactional
    fun runDailySettlement(date: LocalDate) : SettlementResult {
        validateSettlementDate(date)

        val settlementMapByMerchantId = processDailySettlementByChunked(date)
        val settlementList = settlementMapByMerchantId.values.toList()
        val saved = settlementRepository.saveAll(settlementList)

        return SettlementResult(
            settlementDate = date,
            merchantCount = settlementMapByMerchantId.keys.count(),
            totalSettlementAmount = saved.sumOf { it.totalAmount },
            message = "Daily settlement complete"
        )
    }

    private fun processDailySettlementByChunked(date: LocalDate): Map<Long, Settlement> {
        val settlementMapByMerchantId = mutableMapOf<Long, Settlement>()
        var page = 0
        val size = 1000

        do {
            val payments = paymentRepository.findByPaidDateAndIsComplete(date, PageRequest.of(page, size))
            val paymentByMerchant = payments.groupBy { it.merchant }
            paymentByMerchant.forEach { (merchant, payments) ->
                val merchantId = merchant.id
                var settlement = settlementMapByMerchantId[merchantId]

                if (settlement == null) {
                    settlementMapByMerchantId[merchantId] = Settlement.createPending(merchant, date, payments)
                } else {
                    settlement.addPayments(payments)
                }
            }
            page++
        } while (payments.hasNext())

        return settlementMapByMerchantId
    }

    private fun validateSettlementDate(settlementDate: LocalDate) {
        val now = LocalDate.now()
        if (now.isBefore(settlementDate)) {
            throw GlobalHttpException(HttpStatus.BAD_REQUEST.value(), "Settlement date must be before $settlementDate")
        }

        val isExist = settlementRepository.existsBySettlementDate(settlementDate)
        if (isExist) {
            throw GlobalHttpException(HttpStatus.CONFLICT.value(), "Settlement already exists")
        }
    }

    fun getSettlementSummary(merchantId: Long, month: YearMonth): SettlementResponse {
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()
        val merchant = merchantRepository.findByIdOrNull(merchantId)
            ?: throw GlobalHttpException(HttpStatus.NOT_FOUND.value(), "Merchant not found")
        val settlementList = settlementRepository.findByMerchantAndSettlementDateBetween(merchant, startDate, endDate)

        return SettlementResponse(
            merchantId = merchant.id,
            month = month,
            settlements = settlementList.map {
                SettlementData.toData(it)
            },
            summary = getSettlementSummary(settlementList)
        )
    }

    private fun getSettlementSummary(settlements: List<Settlement>): SettlementResponse.SettlementSummary {
        return SettlementResponse.SettlementSummary(
            totalNetAmount = settlements.sumOf { it.netAmount },
            totalFeeAmount = settlements.sumOf { it.totalFee },
            totalPaymentCount = settlements.sumOf { it.paymentCount }
        )
    }

    @Transactional
    fun confirm(settlementId: Long) {
        val settlement = settlementRepository.findByIdWithLock(settlementId)
            ?: throw GlobalHttpException(HttpStatus.NOT_FOUND.value(), "Settlement not found")

        if (settlement.status != SettlementStatus.PENDING) {
            return
        }

        settlementRepository.save(settlement.confirm())
    }


    data class SettlementResult(
        val settlementDate: LocalDate,
        val merchantCount: Int,
        val totalSettlementAmount: BigDecimal,
        val message: String
    )
}