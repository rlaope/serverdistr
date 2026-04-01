package com.khope.payment.domain.merchant

import org.springframework.data.jpa.repository.JpaRepository

interface MerchantRepository : JpaRepository<Merchant, Long> {
}