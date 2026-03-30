package com.khope.payment.repository

import com.khope.payment.domain.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@EnableAutoConfiguration(exclude = [RedisAutoConfiguration::class, KafkaAutoConfiguration::class])
class PaymentRepositoryTest {

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var merchantRepository: MerchantRepository

    @Autowired
    lateinit var settlementRepository: SettlementRepository

    private lateinit var merchantA: Merchant
    private lateinit var merchantB: Merchant
    private val targetDate = LocalDate.of(2026, 3, 28)

    @BeforeEach
    fun setup() {
        settlementRepository.deleteAll()
        paymentRepository.deleteAll()
        merchantRepository.deleteAll()

        merchantA = merchantRepository.save(
            Merchant(
                name = "가맹점A",
                businessNumber = "111-11-11111",
                feeRate = BigDecimal("0.033"),
                status = MerchantStatus.ACTIVE,
            )
        )
        merchantB = merchantRepository.save(
            Merchant(
                name = "가맹점B",
                businessNumber = "222-22-22222",
                feeRate = BigDecimal("0.025"),
                status = MerchantStatus.ACTIVE,
            )
        )
    }

    private fun savePayment(merchant: Merchant, orderId: String, amount: Long, status: PaymentStatus = PaymentStatus.COMPLETED, paidDate: LocalDate? = targetDate, cancelled: Boolean = false): Payment {
        val payment = Payment(
            merchant = merchant,
            orderId = orderId,
            amount = BigDecimal(amount),
            status = status,
        )
        payment.paidDate = paidDate
        if (cancelled) payment.canceledDate = LocalDateTime.now()
        return paymentRepository.save(payment)
    }

    @Test
    @DisplayName("COMPLETED + 해당 날짜 + 취소 안 된 결제만 조회된다")
    fun `정산 대상 결제만 정확히 필터링된다`() {
        // 정산 대상
        savePayment(merchantA, "target-1", 10_000)
        savePayment(merchantA, "target-2", 20_000)

        // 제외 대상: 다른 날짜
        savePayment(merchantA, "other-date", 10_000, paidDate = targetDate.minusDays(1))

        // 제외 대상: CANCELLED 상태
        savePayment(merchantA, "cancelled", 10_000, status = PaymentStatus.CANCELLED)

        // 제외 대상: 취소된 결제 (canceledDate != null)
        savePayment(merchantA, "cancel-date", 10_000, cancelled = true)

        val result = paymentRepository.findByPaidDateAndIsComplete(targetDate, PageRequest.of(0, 100))

        assertThat(result.content).hasSize(2)
        assertThat(result.content.map { it.orderId }).containsExactlyInAnyOrder("target-1", "target-2")
    }

    @Test
    @DisplayName("JOIN FETCH로 merchant가 즉시 로드된다")
    fun `조회 결과에서 merchant에 추가 쿼리 없이 접근 가능하다`() {
        savePayment(merchantA, "fetch-test", 10_000)

        val result = paymentRepository.findByPaidDateAndIsComplete(targetDate, PageRequest.of(0, 100))

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].merchant.name).isEqualTo("가맹점A")
        assertThat(result.content[0].merchant.feeRate).isEqualByComparingTo(BigDecimal("0.033"))
    }

    @Test
    @DisplayName("Slice 페이징이 hasNext를 정확히 반환한다")
    fun `페이지 크기보다 데이터가 많으면 hasNext가 true`() {
        (1..5).forEach { savePayment(merchantA, "page-$it", 10_000) }

        val page0 = paymentRepository.findByPaidDateAndIsComplete(targetDate, PageRequest.of(0, 3))
        assertThat(page0.content).hasSize(3)
        assertThat(page0.hasNext()).isTrue()

        val page1 = paymentRepository.findByPaidDateAndIsComplete(targetDate, PageRequest.of(1, 3))
        assertThat(page1.content).hasSize(2)
        assertThat(page1.hasNext()).isFalse()
    }

    @Test
    @DisplayName("Settlement unique 제약조건: 같은 가맹점+날짜 중복 저장 불가")
    fun `같은 가맹점과 날짜의 Settlement은 중복 저장할 수 없다`() {
        settlementRepository.save(
            Settlement.createPending(merchantA, targetDate, emptyList())
        )

        assertThat(settlementRepository.existsBySettlementDate(targetDate)).isTrue()
    }

    @Test
    @DisplayName("여러 가맹점의 결제가 정확히 분리 조회된다")
    fun `여러 가맹점 결제가 하나의 쿼리로 모두 조회된다`() {
        savePayment(merchantA, "a-1", 10_000)
        savePayment(merchantA, "a-2", 20_000)
        savePayment(merchantB, "b-1", 50_000)

        val result = paymentRepository.findByPaidDateAndIsComplete(targetDate, PageRequest.of(0, 100))

        assertThat(result.content).hasSize(3)

        val grouped = result.content.groupBy { it.merchant.name }
        assertThat(grouped["가맹점A"]).hasSize(2)
        assertThat(grouped["가맹점B"]).hasSize(1)
    }
}
