package com.khope.payment.domain

import io.lettuce.core.dynamic.annotation.Param
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface SettlementRepository : JpaRepository<Settlement, Long> {
    fun existsBySettlementDate(settlementDate: LocalDate): Boolean
    fun findByMerchantAndSettlementDateBetween(
        merchant: Merchant,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Settlement>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :settlementId")
    fun findByIdWithLock(@Param("settlementId") settlementId: Long): Settlement?
}