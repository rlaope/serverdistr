package com.khope.payment.domain.transfer

import org.springframework.data.jpa.repository.JpaRepository

interface TransferHistoryRepository : JpaRepository<TransferHistory, Long> {

    fun findAllByFromAccountIdOrToAccountId(fromAccountId: Long, toAccountId: Long): List<TransferHistory>
}