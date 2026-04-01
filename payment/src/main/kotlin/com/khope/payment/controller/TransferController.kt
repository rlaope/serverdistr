package com.khope.payment.controller

import com.khope.payment.domain.transfer.TransferStatus
import com.khope.payment.service.TransferService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/transfer")
class TransferController(
    private val transferService: TransferService
) {

    @PostMapping
    fun transfer(@RequestBody request: TransferRequest): ResponseEntity<TransferResponse> {
        val response = transferService.transfer(request.fromAccountId, request.toAccountId, request.amount)
        return ResponseEntity.ok(response)
    }

    data class TransferRequest(
        val fromAccountId: Long,
        val toAccountId: Long,
        val amount: BigDecimal,
    )

    data class TransferResponse(
        val transferId: String,
        val fromAccountId: Long,
        val toAccountId: Long,
        val amount: BigDecimal,
        val status: TransferStatus,
        val fromBalance: BigDecimal,
        val toBalance: BigDecimal,
        val createdAt: LocalDateTime,
    )
}