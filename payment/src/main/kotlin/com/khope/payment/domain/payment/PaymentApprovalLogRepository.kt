package com.khope.payment.domain.payment

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentApprovalLogRepository : JpaRepository<PaymentApprovalLog, Long> {
}