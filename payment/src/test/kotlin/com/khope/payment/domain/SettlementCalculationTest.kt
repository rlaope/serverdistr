package com.khope.payment.domain

import com.khope.payment.domain.merchant.Merchant
import com.khope.payment.domain.merchant.MerchantStatus
import com.khope.payment.domain.payment.Payment
import com.khope.payment.domain.payment.PaymentStatus
import com.khope.payment.domain.settlement.Settlement
import com.khope.payment.domain.settlement.SettlementStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 시나리오 1: 기본 정산 금액 계산 검증
 *
 * 가맹점A(수수료 3.3%)의 3/28 결제 3건: 10,000원, 25,000원, 7,500원
 * - 10,000 * 0.033 = 330.0 → 절사 → 330
 * - 25,000 * 0.033 = 825.0 → 절사 → 825
 * - 7,500 * 0.033 = 247.5 → 절사 → 247
 * - totalFee = 1,402
 * - totalAmount = 42,500
 * - netAmount = 41,098
 */
class SettlementCalculationTest {

    private val date = LocalDate.of(2026, 3, 28)

    private fun createMerchant(feeRate: String): Merchant {
        return Merchant(
            name = "테스트가맹점",
            businessNumber = "123-45-67890",
            feeRate = BigDecimal(feeRate),
            status = MerchantStatus.ACTIVE,
        )
    }

    private fun createPayment(merchant: Merchant, orderId: String, amount: Long): Payment {
        val payment = Payment(
            merchant = merchant,
            orderId = orderId,
            amount = BigDecimal(amount),
            status = PaymentStatus.COMPLETED,
        )
        payment.paidDate = date
        return payment
    }

    @Test
    @DisplayName("시나리오1: 기본 정산 - 건별 수수료 원단위 절사 후 합산")
    fun `기본 정산 금액이 건별 절사 후 합산으로 정확히 계산된다`() {
        val merchant = createMerchant("0.033")
        val payments = listOf(
            createPayment(merchant, "order-1", 10_000),
            createPayment(merchant, "order-2", 25_000),
            createPayment(merchant, "order-3", 7_500),
        )

        val settlement = Settlement.createPending(merchant, date, payments)

        assertThat(settlement.totalAmount).isEqualByComparingTo(BigDecimal("42500"))
        assertThat(settlement.totalFee).isEqualByComparingTo(BigDecimal("1402"))
        assertThat(settlement.netAmount).isEqualByComparingTo(BigDecimal("41098"))
        assertThat(settlement.paymentCount).isEqualTo(3)
        assertThat(settlement.status).isEqualTo(SettlementStatus.PENDING)
    }

    @Test
    @DisplayName("시나리오3: 가맹점별 수수료율이 다르게 적용된다")
    fun `서로 다른 수수료율이 각 가맹점에 정확히 적용된다`() {
        val merchantA = createMerchant("0.033") // 3.3%
        val merchantB = createMerchant("0.025") // 2.5%
        val merchantC = createMerchant("0.040") // 4.0%

        val paymentsA = listOf(createPayment(merchantA, "a-1", 100_000))
        val paymentsB = listOf(createPayment(merchantB, "b-1", 100_000))
        val paymentsC = listOf(createPayment(merchantC, "c-1", 100_000))

        val settlementA = Settlement.createPending(merchantA, date, paymentsA)
        val settlementB = Settlement.createPending(merchantB, date, paymentsB)
        val settlementC = Settlement.createPending(merchantC, date, paymentsC)

        // A: 100,000 * 0.033 = 3,300
        assertThat(settlementA.totalFee).isEqualByComparingTo(BigDecimal("3300"))
        assertThat(settlementA.netAmount).isEqualByComparingTo(BigDecimal("96700"))

        // B: 100,000 * 0.025 = 2,500
        assertThat(settlementB.totalFee).isEqualByComparingTo(BigDecimal("2500"))
        assertThat(settlementB.netAmount).isEqualByComparingTo(BigDecimal("97500"))

        // C: 100,000 * 0.040 = 4,000
        assertThat(settlementC.totalFee).isEqualByComparingTo(BigDecimal("4000"))
        assertThat(settlementC.netAmount).isEqualByComparingTo(BigDecimal("96000"))
    }

    @Test
    @DisplayName("수수료 절사가 건별로 적용되어 합산 후 계산과 차이가 발생한다")
    fun `건별 절사와 합산 후 절사의 차이를 검증한다`() {
        val merchant = createMerchant("0.033")

        // 17,777 * 0.033 = 586.641 → 절사 → 586
        // 12,345 * 0.033 = 407.385 → 절사 → 407
        // 건별 합산 fee = 993
        val payments = listOf(
            createPayment(merchant, "order-1", 17_777),
            createPayment(merchant, "order-2", 12_345),
        )

        val settlement = Settlement.createPending(merchant, date, payments)

        assertThat(settlement.totalAmount).isEqualByComparingTo(BigDecimal("30122"))
        assertThat(settlement.totalFee).isEqualByComparingTo(BigDecimal("993"))
        assertThat(settlement.netAmount).isEqualByComparingTo(BigDecimal("29129"))

        // 합산 후 계산하면: 30,122 * 0.033 = 994.026 → 절사 → 994 (건별과 1원 차이)
        val wrongFee = BigDecimal("30122").multiply(BigDecimal("0.033")).setScale(0, java.math.RoundingMode.DOWN)
        assertThat(settlement.totalFee).isNotEqualByComparingTo(wrongFee)
    }

    @Test
    @DisplayName("시나리오4: 결제 건이 빈 리스트면 0으로 생성된다")
    fun `결제 건이 없으면 모든 금액이 0이다`() {
        val merchant = createMerchant("0.033")

        val settlement = Settlement.createPending(merchant, date, emptyList())

        assertThat(settlement.totalAmount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(settlement.totalFee).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(settlement.netAmount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(settlement.paymentCount).isEqualTo(0)
    }

    @Test
    @DisplayName("addPayments로 chunk 누적 시 금액이 정확히 합산된다")
    fun `addPayments 호출로 누적되는 금액이 createPending과 일치한다`() {
        val merchant = createMerchant("0.033")

        val chunk1 = listOf(
            createPayment(merchant, "order-1", 10_000),
            createPayment(merchant, "order-2", 25_000),
        )
        val chunk2 = listOf(
            createPayment(merchant, "order-3", 7_500),
        )

        // chunk 방식: createPending + addPayments
        val chunked = Settlement.createPending(merchant, date, chunk1)
        chunked.addPayments(chunk2)

        // 한번에 방식
        val allAtOnce = Settlement.createPending(merchant, date, chunk1 + chunk2)

        assertThat(chunked.totalAmount).isEqualByComparingTo(allAtOnce.totalAmount)
        assertThat(chunked.totalFee).isEqualByComparingTo(allAtOnce.totalFee)
        assertThat(chunked.netAmount).isEqualByComparingTo(allAtOnce.netAmount)
        assertThat(chunked.paymentCount).isEqualTo(allAtOnce.paymentCount)
    }

    @Test
    @DisplayName("confirm 호출 시 상태가 CONFIRMED로 변경된다")
    fun `confirm은 PENDING을 CONFIRMED로 변경한다`() {
        val merchant = createMerchant("0.033")
        val settlement = Settlement.createPending(merchant, date, emptyList())

        assertThat(settlement.status).isEqualTo(SettlementStatus.PENDING)

        settlement.confirm()

        assertThat(settlement.status).isEqualTo(SettlementStatus.CONFIRMED)
    }
}
