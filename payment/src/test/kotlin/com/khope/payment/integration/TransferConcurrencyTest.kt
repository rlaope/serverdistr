package com.khope.payment.integration

import com.khope.payment.domain.account.Account
import com.khope.payment.domain.account.AccountRepository
import com.khope.payment.domain.account.AccountStatus
import com.khope.payment.domain.transfer.TransferHistoryRepository
import com.khope.payment.service.TransferService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import redis.embedded.RedisServer
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("integration")
class TransferConcurrencyTest {

    companion object {
        private lateinit var redisServer: RedisServer
        private const val REDIS_PORT = 16379

        @BeforeAll
        @JvmStatic
        fun startRedis() {
            redisServer = RedisServer.newRedisServer()
                .port(REDIS_PORT)
                .build()
            redisServer.start()
        }

        @AfterAll
        @JvmStatic
        fun stopRedis() {
            redisServer.stop()
        }
    }

    @TestConfiguration
    class RedissonTestConfig {
        @Bean
        @Primary
        fun redissonClient(): RedissonClient {
            val config = Config()
            config.useSingleServer().address = "redis://localhost:$REDIS_PORT"
            return Redisson.create(config)
        }
    }

    @MockBean lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired lateinit var transferService: TransferService
    @Autowired lateinit var accountRepository: AccountRepository
    @Autowired lateinit var transferHistoryRepository: TransferHistoryRepository
    @Autowired lateinit var redissonClient: RedissonClient

    private lateinit var accountA: Account
    private lateinit var accountB: Account
    private lateinit var accountC: Account

    @BeforeEach
    fun setup() {
        transferHistoryRepository.deleteAll()
        accountRepository.deleteAll()
        redissonClient.keys.flushdb()

        accountA = accountRepository.save(
            Account(
                userId = 1L, accountNumber = "1111-1111-1111",
                balance = BigDecimal("1000000"),
                dailyTransferLimit = BigDecimal("5000000"),
                perTransferLimit = BigDecimal("1000000"),
                status = AccountStatus.ACTIVE, version = 0L
            )
        )

        accountB = accountRepository.save(
            Account(
                userId = 2L, accountNumber = "2222-2222-2222",
                balance = BigDecimal("1000000"),
                dailyTransferLimit = BigDecimal("5000000"),
                perTransferLimit = BigDecimal("1000000"),
                status = AccountStatus.ACTIVE, version = 0L
            )
        )

        accountC = accountRepository.save(
            Account(
                userId = 3L, accountNumber = "3333-3333-3333",
                balance = BigDecimal("1000000"),
                dailyTransferLimit = BigDecimal("5000000"),
                perTransferLimit = BigDecimal("1000000"),
                status = AccountStatus.ACTIVE, version = 0L
            )
        )
    }

    // ========================================================
    // 시나리오 3: 동시 양방향 송금 (데드락 방지)
    // ========================================================

    @Test
    @DisplayName("시나리오3: 동시 양방향 송금 시 데드락 없이 정합성 유지")
    fun `동시 양방향 송금 시 데드락 없이 정합성 유지`() {
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val successCount = AtomicInteger(0)

        executor.submit {
            try {
                transferService.transfer(accountA.id, accountB.id, BigDecimal("300000"))
                successCount.incrementAndGet()
            } catch (_: Exception) {
            } finally {
                latch.countDown()
            }
        }

        executor.submit {
            try {
                transferService.transfer(accountB.id, accountA.id, BigDecimal("200000"))
                successCount.incrementAndGet()
            } catch (_: Exception) {
            } finally {
                latch.countDown()
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        val a = accountRepository.findById(accountA.id).get()
        val b = accountRepository.findById(accountB.id).get()

        assertThat(successCount.get()).isEqualTo(2)
        assertThat(a.balance).isEqualByComparingTo(BigDecimal("900000"))
        assertThat(b.balance).isEqualByComparingTo(BigDecimal("1100000"))
    }

    // ========================================================
    // 시나리오 4: 동시 동일 계좌 출금 (경쟁 조건)
    // ========================================================

    @Test
    @DisplayName("시나리오4: 동시 동일 계좌 출금 시 하나만 성공")
    fun `동시 동일 계좌 출금 시 잔액 정합성 유지`() {
        accountA.balance = BigDecimal("500000")
        accountRepository.save(accountA)

        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        executor.submit {
            try {
                transferService.transfer(accountA.id, accountB.id, BigDecimal("300000"))
                successCount.incrementAndGet()
            } catch (_: Exception) {
                failCount.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        executor.submit {
            try {
                transferService.transfer(accountA.id, accountC.id, BigDecimal("300000"))
                successCount.incrementAndGet()
            } catch (_: Exception) {
                failCount.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        val a = accountRepository.findById(accountA.id).get()

        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(1)
        assertThat(a.balance).isEqualByComparingTo(BigDecimal("200000"))
    }

    // ========================================================
    // 일일 한도 동시성 테스트
    // ========================================================

    @Test
    @DisplayName("동시 10건 송금 시 일일 한도 정확히 관리")
    fun `동시 송금 시 일일 한도가 정확히 관리된다`() {
        accountA.balance = BigDecimal("10000000")
        accountRepository.save(accountA)

        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(10)
        val successCount = AtomicInteger(0)

        repeat(10) {
            executor.submit {
                try {
                    transferService.transfer(accountA.id, accountB.id, BigDecimal("600000"))
                    successCount.incrementAndGet()
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 600,000 x 8 = 4,800,000 (한도 5,000,000 내), 9번째부터 초과
        assertThat(successCount.get()).isLessThanOrEqualTo(8)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1)

        val a = accountRepository.findById(accountA.id).get()
        val expectedDeducted = BigDecimal("600000").multiply(BigDecimal(successCount.get()))
        assertThat(a.balance).isEqualByComparingTo(BigDecimal("10000000").subtract(expectedDeducted))
    }
}
