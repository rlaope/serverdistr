package com.khope.payment.service

import com.khope.payment.domain.account.Account
import com.khope.payment.domain.account.AccountRepository
import com.khope.payment.domain.account.AccountStatus
import com.khope.payment.domain.transfer.TransferCache
import com.khope.payment.domain.transfer.TransferHistory
import com.khope.payment.domain.transfer.TransferHistoryRepository
import com.khope.payment.domain.transfer.TransferStatus
import com.khope.payment.exception.GlobalHttpException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

    @Mock lateinit var accountRepository: AccountRepository
    @Mock lateinit var transferHistoryRepository: TransferHistoryRepository
    @Mock lateinit var transferCache: TransferCache

    private lateinit var transferService: TransferService

    private lateinit var accountA: Account
    private lateinit var accountB: Account

    @BeforeEach
    fun setup() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization()
        }
        transferService = TransferService(transferHistoryRepository, accountRepository, transferCache)

        accountA = Account(
            userId = 1L, accountNumber = "1234-5678-9012",
            balance = BigDecimal("1000000"), dailyTransferLimit = BigDecimal("5000000"),
            perTransferLimit = BigDecimal("1000000"), status = AccountStatus.ACTIVE, version = 0L
        )
        setId(accountA, 1L)

        accountB = Account(
            userId = 2L, accountNumber = "9876-5432-1098",
            balance = BigDecimal("500000"), dailyTransferLimit = BigDecimal("5000000"),
            perTransferLimit = BigDecimal("1000000"), status = AccountStatus.ACTIVE, version = 0L
        )
        setId(accountB, 2L)
    }

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun setId(entity: Any, id: Long) {
        val field = entity.javaClass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    private fun stubAccounts() {
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(accountA))
        whenever(accountRepository.findById(2L)).thenReturn(Optional.of(accountB))
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] }
        whenever(transferHistoryRepository.save(any<TransferHistory>())).thenAnswer { it.arguments[0] }
    }

    // ========================================================
    // 시나리오 1: 정상 송금
    // ========================================================

    @Test
    @DisplayName("시나리오1: 정상 송금 - A→B 100,000원")
    fun `정상 송금 시 잔액이 정확히 변경된다`() {
        stubAccounts()
        whenever(transferCache.getDailyUsage("1")).thenReturn(BigDecimal.ZERO)

        val response = transferService.transfer(1L, 2L, BigDecimal("100000"))

        assertThat(response.fromBalance).isEqualByComparingTo(BigDecimal("900000"))
        assertThat(response.toBalance).isEqualByComparingTo(BigDecimal("600000"))
        assertThat(response.status).isEqualTo(TransferStatus.SUCCESS)

        verify(accountRepository, times(2)).save(any<Account>())
        verify(transferHistoryRepository).save(argThat { status == TransferStatus.SUCCESS })
    }

    // ========================================================
    // 시나리오 2: 잔액 부족
    // ========================================================

    @Test
    @DisplayName("시나리오2: 잔액 부족 시 400 에러")
    fun `잔액보다 큰 금액 송금 시 예외가 발생한다`() {
        stubAccounts()
        whenever(transferCache.getDailyUsage("1")).thenReturn(BigDecimal.ZERO)

        assertThatThrownBy { transferService.transfer(1L, 2L, BigDecimal("1500000")) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code")
            .isEqualTo(400)

        verify(accountRepository, never()).saveAll(any<List<Account>>())
    }

    // ========================================================
    // 시나리오 5: 1회 한도 초과
    // ========================================================

    @Test
    @DisplayName("시나리오5: 1회 한도(100만원) 초과 시 TRANSFER_LIMIT_EXCEEDED")
    fun `1회 한도 초과 시 예외가 발생한다`() {
        stubAccounts()
        accountA = Account(
            userId = 1L, accountNumber = "1234-5678-9012",
            balance = BigDecimal("5000000"), dailyTransferLimit = BigDecimal("5000000"),
            perTransferLimit = BigDecimal("1000000"), status = AccountStatus.ACTIVE, version = 0L
        )
        setId(accountA, 1L)
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(accountA))

        assertThatThrownBy { transferService.transfer(1L, 2L, BigDecimal("1500000")) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("message")
            .isEqualTo("TRANSFER_LIMIT_EXCEEDED")
    }

    // ========================================================
    // 시나리오 6: 일일 한도 초과
    // ========================================================

    @Test
    @DisplayName("시나리오6: 일일 한도(500만원) 초과 시 DAILY_LIMIT_EXCEEDED")
    fun `일일 누적 한도 초과 시 예외가 발생한다`() {
        stubAccounts()
        whenever(transferCache.getDailyUsage("1")).thenReturn(BigDecimal("4500000"))

        assertThatThrownBy { transferService.transfer(1L, 2L, BigDecimal("600000")) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("message")
            .isEqualTo("DAILY_LIMIT_EXCEEDED")
    }

    // ========================================================
    // 시나리오 7: 자기 자신에게 송금
    // ========================================================

    @Test
    @DisplayName("시나리오7: 자기 자신에게 송금 시 SELF_TRANSFER_NOT_ALLOWED")
    fun `자기 자신에게 송금하면 예외가 발생한다`() {
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(accountA))

        assertThatThrownBy { transferService.transfer(1L, 1L, BigDecimal("100000")) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("message")
            .isEqualTo("SELF_TRANSFER_NOT_ALLOWED")
    }

    // ========================================================
    // 추가: 계좌 없음
    // ========================================================

    @Test
    @DisplayName("존재하지 않는 계좌로 송금 시 ACCOUNT_NOT_FOUND")
    fun `계좌가 없으면 404 예외가 발생한다`() {
        whenever(accountRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { transferService.transfer(999L, 2L, BigDecimal("100000")) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code", "message")
            .containsExactly(404, "ACCOUNT_NOT_FOUND")
    }

    // ========================================================
    // 추가: 동결 계좌
    // ========================================================

    @Test
    @DisplayName("동결된 출금 계좌에서 송금 시 ACCOUNT_NOT_AVAILABLE")
    fun `출금 계좌가 동결이면 예외가 발생한다`() {
        val frozenAccount = Account(
            userId = 3L, accountNumber = "1111-2222-3333",
            balance = BigDecimal("1000000"), dailyTransferLimit = BigDecimal("5000000"),
            perTransferLimit = BigDecimal("1000000"), status = AccountStatus.FROZEN, version = 0L
        )
        setId(frozenAccount, 3L)
        whenever(accountRepository.findById(3L)).thenReturn(Optional.of(frozenAccount))
        whenever(accountRepository.findById(2L)).thenReturn(Optional.of(accountB))

        assertThatThrownBy { transferService.transfer(3L, 2L, BigDecimal("100000")) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("message")
            .isEqualTo("ACCOUNT_NOT_AVAILABLE")
    }

    @Test
    @DisplayName("동결된 입금 계좌로 송금 시 ACCOUNT_NOT_AVAILABLE")
    fun `입금 계좌가 동결이면 예외가 발생한다`() {
        val frozenAccount = Account(
            userId = 3L, accountNumber = "1111-2222-3333",
            balance = BigDecimal("1000000"), dailyTransferLimit = BigDecimal("5000000"),
            perTransferLimit = BigDecimal("1000000"), status = AccountStatus.FROZEN, version = 0L
        )
        setId(frozenAccount, 3L)
        whenever(accountRepository.findById(1L)).thenReturn(Optional.of(accountA))
        whenever(accountRepository.findById(3L)).thenReturn(Optional.of(frozenAccount))

        assertThatThrownBy { transferService.transfer(1L, 3L, BigDecimal("100000")) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("message")
            .isEqualTo("ACCOUNT_NOT_AVAILABLE")
    }
}
