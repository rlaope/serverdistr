package com.khope.payment.service

import com.khope.payment.controller.TransferController
import com.khope.payment.domain.account.Account
import com.khope.payment.domain.account.AccountRepository
import com.khope.payment.domain.account.AccountStatus
import com.khope.payment.domain.transfer.TransferCache
import com.khope.payment.domain.transfer.TransferHistory
import com.khope.payment.domain.transfer.TransferHistoryRepository
import com.khope.payment.domain.transfer.TransferStatus
import com.khope.payment.exception.GlobalHttpException
import jakarta.transaction.Transactional
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.util.UUID

@Service
class TransferService(
    private val transferHistoryRepository: TransferHistoryRepository,
    private val accountRepository: AccountRepository,
    private val transferCache: TransferCache,
) {

    /**
     * 낙관적 락(@Version) + ID 순서 조회/저장으로 데드락 방지
     * UPDATE 순서가 엇갈리면 DB 레벨 데드락 발생 가능 → 항상 작은 ID 먼저 처리
     */
    @Transactional
    @Retryable(
        value = [OptimisticLockingFailureException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0)
    )
    fun transfer(fromAccountId: Long, toAccountId: Long, amount: BigDecimal): TransferController.TransferResponse {
        validateSelfTransfer(fromAccountId, toAccountId)

        // 데드락 방지: 항상 작은 ID 먼저 조회/저장
        val (firstId, secondId) = if (fromAccountId < toAccountId) {
            fromAccountId to toAccountId
        } else {
            toAccountId to fromAccountId
        }

        val firstAccount = findAccount(firstId)
        val secondAccount = findAccount(secondId)

        val fromAccount = if (fromAccountId == firstId) firstAccount else secondAccount
        val toAccount = if (toAccountId == firstId) firstAccount else secondAccount

        validateAccountStatus(fromAccount, toAccount)
        validateTransferLimit(fromAccount, amount)

        val response = transferAmount(firstAccount, secondAccount, fromAccount, toAccount, amount)

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                transferCache.increment(fromAccount.id.toString(), amount)
            }
        })

        return response
    }

    private fun validateSelfTransfer(fromAccountId: Long, toAccountId: Long) {
        if (fromAccountId == toAccountId) {
            throw GlobalHttpException(400, "SELF_TRANSFER_NOT_ALLOWED")
        }
    }

    private fun findAccount(accountId: Long): Account {
        return accountRepository.findById(accountId)
            .orElseThrow { GlobalHttpException(404, "ACCOUNT_NOT_FOUND") }
    }

    private fun validateAccountStatus(fromAccount: Account, toAccount: Account) {
        if (fromAccount.status != AccountStatus.ACTIVE) {
            throw GlobalHttpException(400, "ACCOUNT_NOT_AVAILABLE")
        }

        if (toAccount.status != AccountStatus.ACTIVE) {
            throw GlobalHttpException(400, "ACCOUNT_NOT_AVAILABLE")
        }
    }

    private fun validateTransferLimit(fromAccount: Account, amount: BigDecimal) {
        if (fromAccount.perTransferLimit < amount) {
            throw GlobalHttpException(400, "TRANSFER_LIMIT_EXCEEDED")
        }

        val dailyUsage = transferCache.getDailyUsage(fromAccount.id.toString())
        if (dailyUsage.add(amount) > fromAccount.dailyTransferLimit) {
            throw GlobalHttpException(400, "DAILY_LIMIT_EXCEEDED")
        }
    }

    // ID 순서대로 save하여 UPDATE 순서 통일 → 데드락 방지
    private fun transferAmount(
        firstAccount: Account,
        secondAccount: Account,
        fromAccount: Account,
        toAccount: Account,
        amount: BigDecimal
    ): TransferController.TransferResponse {
        val fromBalance = fromAccount.withdraw(amount)
        val toBalance = toAccount.addAmount(amount)

        accountRepository.save(firstAccount)
        accountRepository.save(secondAccount)

        val saved = transferHistoryRepository.save(
            TransferHistory(
                transferId = UUID.randomUUID().toString(),
                fromAccount = fromAccount,
                toAccount = toAccount,
                amount = amount,
                status = TransferStatus.SUCCESS,
            )
        )

        return TransferController.TransferResponse(
            transferId = saved.transferId,
            fromAccountId = fromAccount.id,
            toAccountId = toAccount.id,
            status = saved.status,
            amount = saved.amount,
            fromBalance = fromBalance,
            toBalance = toBalance,
            createdAt = saved.createdAt,
        )
    }
}
