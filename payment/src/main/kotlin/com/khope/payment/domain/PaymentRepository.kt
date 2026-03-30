package com.khope.payment.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface PaymentRepository : JpaRepository<Payment, Long> {

    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.merchant
        WHERE p.paidDate = :paidDate
        AND p.status = com.khope.payment.domain.PaymentStatus.COMPLETED
        AND p.canceledDate IS NULL
    """)
    fun findByPaidDateAndIsComplete(@Param("paidDate") paidDate: LocalDate, pageable: Pageable): Slice<Payment>
}