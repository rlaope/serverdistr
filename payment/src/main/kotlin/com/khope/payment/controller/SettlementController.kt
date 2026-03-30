package com.khope.payment.controller

import com.khope.payment.data.SettlementData
import com.khope.payment.service.SettlementService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@RestController
@RequestMapping("/api/settlements")
class SettlementController(
    private val settlementService: SettlementService
) {

    @PostMapping("/daily")
    fun runDailySettlement(@RequestParam date: LocalDate): ResponseEntity<DailySettlementResponse> {
        val settlementResult = settlementService.runDailySettlement(date)
        return ResponseEntity.ok(
            DailySettlementResponse(
                settlementDate = settlementResult.settlementDate,
                merchantCount = settlementResult.merchantCount,
                totalSettlementAmount = settlementResult.totalSettlementAmount,
                message = settlementResult.message,
            )
        )
    }

    data class DailySettlementResponse(
        val settlementDate: LocalDate,
        val merchantCount: Int,
        val totalSettlementAmount: BigDecimal,
        val message: String
    )

    @GetMapping
    fun getSettlementByMerchantAndDate(
        @RequestParam merchantId: Long,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") month: YearMonth
    ): ResponseEntity<SettlementResponse> {
        val settlementResponse = settlementService.getSettlementSummary(merchantId, month)
        return ResponseEntity.ok()
            .body(settlementResponse)
    }

    data class SettlementResponse(
        val merchantId: Long,
        val month: YearMonth,
        val settlements: List<SettlementData>,
        val summary: SettlementSummary
    ) {
        data class SettlementSummary(
            val totalNetAmount: BigDecimal,
            val totalFeeAmount: BigDecimal,
            val totalPaymentCount: Int
        )
    }

    @PostMapping("/{settlementId}/confirm")
    fun confirmSettlement(@PathVariable settlementId: Long): ResponseEntity<Void> {
        settlementService.confirm(settlementId)
        return ResponseEntity.noContent().build()
    }
}