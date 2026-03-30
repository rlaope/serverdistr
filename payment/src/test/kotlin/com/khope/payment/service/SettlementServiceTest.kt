package com.khope.payment.service

import com.khope.payment.domain.*
import com.khope.payment.exception.GlobalHttpException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class SettlementServiceTest {

    @Mock
    lateinit var merchantRepository: MerchantRepository

    @Mock
    lateinit var settlementRepository: SettlementRepository

    @Mock
    lateinit var paymentRepository: PaymentRepository

    private lateinit var settlementService: SettlementService

    private val date = LocalDate.of(2026, 3, 28)

    @BeforeEach
    fun setup() {
        settlementService = SettlementService(merchantRepository, settlementRepository, paymentRepository)

        lenient().whenever(settlementRepository.saveAll(any<List<Settlement>>()))
            .thenAnswer { it.arguments[0] }
    }

    private var merchantIdSeq = 1L

    private fun merchant(name: String, feeRate: String, businessNumber: String = "111-22-$name"): Merchant {
        val m = Merchant(
            name = name,
            businessNumber = businessNumber,
            feeRate = BigDecimal(feeRate),
            status = MerchantStatus.ACTIVE,
        )
        // @GeneratedValue 필드에 테스트용 ID 세팅
        val idField = Merchant::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(m, merchantIdSeq++)
        return m
    }

    private fun payment(merchant: Merchant, orderId: String, amount: Long): Payment {
        val p = Payment(
            merchant = merchant,
            orderId = orderId,
            amount = BigDecimal(amount),
            status = PaymentStatus.COMPLETED,
        )
        p.paidDate = date
        return p
    }

    private fun stubPayments(vararg slices: List<Payment>) {
        whenever(settlementRepository.existsBySettlementDate(date)).thenReturn(false)

        slices.forEachIndexed { index, payments ->
            val hasNext = index < slices.size - 1
            val pageable = PageRequest.of(index, 1000)
            whenever(paymentRepository.findByPaidDateAndIsComplete(eq(date), eq(pageable)))
                .thenReturn(SliceImpl(payments, pageable, hasNext))
        }
    }

    // ========================================================
    // 시나리오 1: 기본 정산
    // ========================================================

    @Test
    @DisplayName("시나리오1: 기본 정산 - 가맹점A 3.3%, 결제 3건")
    fun `기본 정산이 정확한 금액으로 수행된다`() {
        val merchantA = merchant("가맹점A", "0.033")
        val payments = listOf(
            payment(merchantA, "o1", 10_000),
            payment(merchantA, "o2", 25_000),
            payment(merchantA, "o3", 7_500),
        )

        stubPayments(payments)

        val result = settlementService.runDailySettlement(date)

        assertThat(result.settlementDate).isEqualTo(date)
        assertThat(result.merchantCount).isEqualTo(1)
        assertThat(result.totalSettlementAmount).isEqualByComparingTo(BigDecimal("42500"))
        assertThat(result.message).contains("complete")

        argumentCaptor<List<Settlement>>().apply {
            verify(settlementRepository).saveAll(capture())
            val saved = firstValue
            assertThat(saved).hasSize(1)
            assertThat(saved[0].totalFee).isEqualByComparingTo(BigDecimal("1402"))
            assertThat(saved[0].netAmount).isEqualByComparingTo(BigDecimal("41098"))
            assertThat(saved[0].paymentCount).isEqualTo(3)
        }
    }

    // ========================================================
    // 시나리오 2: 중복 정산 방지
    // ========================================================

    @Test
    @DisplayName("시나리오2: 이미 정산된 날짜 재실행 시 409 Conflict")
    fun `이미 정산이 존재하면 GlobalHttpException이 발생한다`() {
        whenever(settlementRepository.existsBySettlementDate(date)).thenReturn(true)

        assertThatThrownBy { settlementService.runDailySettlement(date) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code", "message")
            .containsExactly(409, "Settlement already exists")

        verify(paymentRepository, never()).findByPaidDateAndIsComplete(any(), any())
    }

    // ========================================================
    // 시나리오 3: 여러 가맹점 동시 정산
    // ========================================================

    @Test
    @DisplayName("시나리오3: 가맹점별 수수료율이 다르게 적용된다")
    fun `여러 가맹점이 각각의 수수료율로 정산된다`() {
        val merchantA = merchant("가맹점A", "0.033", "111-11-11111")
        val merchantB = merchant("가맹점B", "0.025", "222-22-22222")

        val payments = listOf(
            payment(merchantA, "a1", 100_000),
            payment(merchantB, "b1", 100_000),
        )

        stubPayments(payments)

        val result = settlementService.runDailySettlement(date)

        assertThat(result.merchantCount).isEqualTo(2)

        argumentCaptor<List<Settlement>>().apply {
            verify(settlementRepository).saveAll(capture())
            val saved = firstValue

            assertThat(saved).hasSize(2)

            val settlementA = saved.find { it.merchant.name == "가맹점A" }!!
            val settlementB = saved.find { it.merchant.name == "가맹점B" }!!

            // A: 100,000 * 0.033 = 3,300
            assertThat(settlementA.totalFee).isEqualByComparingTo(BigDecimal("3300"))
            assertThat(settlementA.netAmount).isEqualByComparingTo(BigDecimal("96700"))

            // B: 100,000 * 0.025 = 2,500
            assertThat(settlementB.totalFee).isEqualByComparingTo(BigDecimal("2500"))
            assertThat(settlementB.netAmount).isEqualByComparingTo(BigDecimal("97500"))
        }
    }

    // ========================================================
    // 시나리오 4: 결제 건이 없는 날짜
    // ========================================================

    @Test
    @DisplayName("시나리오4: 결제 건이 없으면 정산 0건")
    fun `결제 건이 없으면 merchantCount가 0이다`() {
        stubPayments(emptyList())

        val result = settlementService.runDailySettlement(date)

        assertThat(result.merchantCount).isEqualTo(0)
        assertThat(result.totalSettlementAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    // ========================================================
    // 시나리오 5: 대량 데이터 페이징 처리
    // ========================================================

    @Test
    @DisplayName("시나리오5: 페이징으로 여러 chunk가 정확히 합산된다")
    fun `여러 페이지의 결제가 하나의 Settlement로 합산된다`() {
        val merchantA = merchant("가맹점A", "0.033")

        val chunk1 = (1..1000).map { payment(merchantA, "o-$it", 10_000) }
        val chunk2 = (1001..1500).map { payment(merchantA, "o-$it", 10_000) }

        stubPayments(chunk1, chunk2)

        val result = settlementService.runDailySettlement(date)

        assertThat(result.merchantCount).isEqualTo(1)
        // 1500건 * 10,000 = 15,000,000
        assertThat(result.totalSettlementAmount).isEqualByComparingTo(BigDecimal("15000000"))

        argumentCaptor<List<Settlement>>().apply {
            verify(settlementRepository).saveAll(capture())
            val saved = firstValue
            assertThat(saved).hasSize(1)
            assertThat(saved[0].paymentCount).isEqualTo(1500)
            // fee = 1500 * 330 = 495,000
            assertThat(saved[0].totalFee).isEqualByComparingTo(BigDecimal("495000"))
            assertThat(saved[0].netAmount).isEqualByComparingTo(BigDecimal("14505000"))
        }

        // 페이징이 2번 호출되었는지 검증
        verify(paymentRepository, times(2)).findByPaidDateAndIsComplete(any(), any())
    }

    // ========================================================
    // 추가: 미래 날짜 검증
    // ========================================================

    @Test
    @DisplayName("미래 날짜 정산 요청 시 400 Bad Request")
    fun `미래 날짜로 정산하면 예외가 발생한다`() {
        val futureDate = LocalDate.now().plusDays(1)

        assertThatThrownBy { settlementService.runDailySettlement(futureDate) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code")
            .isEqualTo(400)
    }
}
