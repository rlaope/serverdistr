package com.khope.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.khope.payment.controller.PaymentController.ApproveRequest
import com.khope.payment.controller.PaymentController.ApproveResponse
import com.khope.payment.domain.*
import com.khope.payment.exception.GlobalHttpException
import com.khope.payment.util.IdempotentManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PaymentServiceTest {

    @Mock lateinit var paymentRepository: PaymentRepository
    @Mock lateinit var paymentApprovalRepository: PaymentApprovalRepository
    @Mock lateinit var paymentApprovalLogRepository: PaymentApprovalLogRepository
    @Mock lateinit var idempotentManager: IdempotentManager

    private lateinit var paymentService: PaymentService
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val idempotencyKey = "550e8400-e29b-41d4-a716-446655440000"
    private val request = ApproveRequest(orderId = "order-1", amount = BigDecimal("50000"), paymentMethod = "CARD")

    private lateinit var merchant: Merchant
    private lateinit var payment: Payment

    @BeforeEach
    fun setup() {
        paymentService = PaymentService(
            paymentRepository, paymentApprovalRepository, paymentApprovalLogRepository,
            idempotentManager, objectMapper
        )

        merchant = Merchant(
            name = "테스트가맹점", businessNumber = "111-11-11111",
            feeRate = BigDecimal("0.033"), status = MerchantStatus.ACTIVE
        )
        setId(merchant, 1L)

        payment = Payment(
            merchant = merchant, orderId = "order-1",
            amount = BigDecimal("50000"), status = PaymentStatus.COMPLETED
        )
        setId(payment, 100L)
    }

    private fun setId(entity: Any, id: Long) {
        val field = entity.javaClass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    private fun stubFirstRequest() {
        whenever(idempotentManager.hashRequest(request)).thenReturn("hash123")
        whenever(idempotentManager.getStatus(idempotencyKey)).thenReturn(null)
        whenever(paymentRepository.findByOrderId("order-1")).thenReturn(payment)
        whenever(paymentApprovalRepository.save(any<PaymentApproval>())).thenAnswer { invocation ->
            invocation.arguments[0] as PaymentApproval
        }
        whenever(paymentApprovalLogRepository.save(any<PaymentApprovalLog>())).thenAnswer { invocation ->
            invocation.arguments[0] as PaymentApprovalLog
        }
    }

    // ========================================================
    // 시나리오 1: 정상 최초 결제
    // ========================================================

    @Test
    @DisplayName("시나리오1: 최초 요청 - 결제 승인 성공")
    fun `최초 요청 시 결제가 승인되고 Redis에 결과가 저장된다`() {
        stubFirstRequest()

        val response = paymentService.approvePayment(idempotencyKey, request)

        assertThat(response.paymentId).isEqualTo(100L)
        assertThat(response.orderId).isEqualTo("order-1")
        assertThat(response.amount).isEqualByComparingTo(BigDecimal("50000"))
        assertThat(response.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(response.message).isEqualTo("Payment approved successfully")

        verify(idempotentManager).markProcessing(eq(idempotencyKey), eq("hash123"))
        verify(idempotentManager).markCompleted(eq(idempotencyKey), eq("APPROVED"), any())
        verify(paymentApprovalRepository).save(any())
        verify(paymentApprovalLogRepository).save(argThat { action == Action.APPROVED })
    }

    // ========================================================
    // 시나리오 2: 동일 key 재요청 — 캐시 응답 반환
    // ========================================================

    @Test
    @DisplayName("시나리오2: 동일 key 재요청 - 캐시된 응답 반환 + DUPLICATE_REQUESTED 로그")
    fun `이미 승인된 key로 재요청하면 캐시 응답을 반환하고 중복 로그를 남긴다`() {
        val cachedResponse = ApproveResponse(
            paymentId = 100L, orderId = "order-1", amount = BigDecimal("50000"),
            status = PaymentStatus.APPROVED, approvedAt = LocalDateTime.of(2026, 3, 28, 14, 30),
            message = "Payment approved successfully"
        )

        whenever(idempotentManager.hashRequest(request)).thenReturn("hash123")
        whenever(idempotentManager.getStatus(idempotencyKey)).thenReturn("APPROVED")
        whenever(idempotentManager.getRequestHash(idempotencyKey)).thenReturn("hash123")
        whenever(idempotentManager.getCachedResponse(idempotencyKey))
            .thenReturn(objectMapper.writeValueAsString(cachedResponse))

        val approval = PaymentApproval(
            idempotencyKey = idempotencyKey, payment = payment, orderId = "order-1",
            amount = BigDecimal("50000"), status = PaymentStatus.APPROVED,
            approvedAt = LocalDateTime.of(2026, 3, 28, 14, 30)
        )
        whenever(paymentApprovalRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(approval)
        whenever(paymentApprovalLogRepository.save(any<PaymentApprovalLog>())).thenAnswer { invocation ->
            invocation.arguments[0] as PaymentApprovalLog
        }

        val response = paymentService.approvePayment(idempotencyKey, request)

        assertThat(response.paymentId).isEqualTo(100L)
        assertThat(response.status).isEqualTo(PaymentStatus.APPROVED)

        verify(paymentApprovalLogRepository).save(argThat { action == Action.DUPLICATE_REQUESTED })
        verify(idempotentManager, never()).markProcessing(any(), any())
        verify(paymentApprovalRepository, never()).save(any())
    }

    // ========================================================
    // 시나리오 4: key 동일 + body 다름 (422)
    // ========================================================

    @Test
    @DisplayName("시나리오4: 동일 key인데 body가 다르면 422")
    fun `동일 key에 다른 body로 요청하면 422 예외가 발생한다`() {
        whenever(idempotentManager.hashRequest(request)).thenReturn("different-hash")
        whenever(idempotentManager.getStatus(idempotencyKey)).thenReturn("APPROVED")
        whenever(idempotentManager.getRequestHash(idempotencyKey)).thenReturn("original-hash")

        assertThatThrownBy { paymentService.approvePayment(idempotencyKey, request) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code", "message")
            .containsExactly(422, "Request body does not match the original request for this idempotency key")
    }

    // ========================================================
    // 시나리오 5: 처리 중 실패 시 cleanup
    // ========================================================

    @Test
    @DisplayName("시나리오5: 결제 처리 중 예외 발생 시 Redis cleanup")
    fun `결제 처리 실패 시 Redis 키가 삭제되어 재시도 가능하다`() {
        whenever(idempotentManager.hashRequest(request)).thenReturn("hash123")
        whenever(idempotentManager.getStatus(idempotencyKey)).thenReturn(null)
        whenever(paymentRepository.findByOrderId("order-1")).thenReturn(null)

        assertThatThrownBy { paymentService.approvePayment(idempotencyKey, request) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code")
            .isEqualTo(404)

        verify(idempotentManager).markProcessing(eq(idempotencyKey), eq("hash123"))
        verify(idempotentManager).cleanup(idempotencyKey)
    }

    // ========================================================
    // 추가: 금액 불일치
    // ========================================================

    @Test
    @DisplayName("요청 금액과 결제 금액이 다르면 400")
    fun `금액 불일치 시 예외가 발생하고 Redis가 cleanup된다`() {
        val mismatchRequest = ApproveRequest(orderId = "order-1", amount = BigDecimal("30000"), paymentMethod = "CARD")

        whenever(idempotentManager.hashRequest(mismatchRequest)).thenReturn("hash456")
        whenever(idempotentManager.getStatus(idempotencyKey)).thenReturn(null)
        whenever(paymentRepository.findByOrderId("order-1")).thenReturn(payment)

        assertThatThrownBy { paymentService.approvePayment(idempotencyKey, mismatchRequest) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code")
            .isEqualTo(400)

        verify(idempotentManager).cleanup(idempotencyKey)
    }

    // ========================================================
    // 추가: CASH 결제 수단 거부
    // ========================================================

    @Test
    @DisplayName("CASH 결제 수단은 400")
    fun `CASH 결제 수단 요청 시 예외가 발생한다`() {
        val cashRequest = ApproveRequest(orderId = "order-1", amount = BigDecimal("50000"), paymentMethod = "CASH")

        whenever(idempotentManager.hashRequest(cashRequest)).thenReturn("hash789")
        whenever(idempotentManager.getStatus(idempotencyKey)).thenReturn(null)
        whenever(paymentRepository.findByOrderId("order-1")).thenReturn(payment)

        assertThatThrownBy { paymentService.approvePayment(idempotencyKey, cashRequest) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code")
            .isEqualTo(400)

        verify(idempotentManager).cleanup(idempotencyKey)
    }
}
