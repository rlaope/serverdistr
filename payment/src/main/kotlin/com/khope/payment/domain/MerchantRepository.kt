package com.khope.payment.domain

import org.springframework.data.jpa.repository.JpaRepository

interface MerchantRepository : JpaRepository<Merchant, Long> {
}