package com.khope.payment.util

import com.khope.payment.exception.GlobalHttpException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.redisson.api.RLock
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@ExtendWith(MockitoExtension::class)
class IdempotentAopTest {

    @Mock lateinit var idempotentManager: IdempotentManager
    @Mock lateinit var joinPoint: ProceedingJoinPoint
    @Mock lateinit var lock: RLock

    private lateinit var aspect: IdempotentAop
    private val idempotent = IdempotentAopTest::class.java
        .getDeclaredMethod("annotated")
        .getAnnotation(Idempotent::class.java)

    @Idempotent
    private fun annotated() {}

    @BeforeEach
    fun setup() {
        aspect = IdempotentAop(idempotentManager)
    }

    private fun setRequestHeader(key: String?, value: String? = null) {
        val request = MockHttpServletRequest()
        if (key != null && value != null) {
            request.addHeader(key, value)
        }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    // ========================================================
    // 시나리오 6: Idempotency-Key 헤더 누락 (400)
    // ========================================================

    @Test
    @DisplayName("시나리오6: Idempotency-Key 헤더 누락 시 400")
    fun `헤더가 없으면 400 예외가 발생한다`() {
        setRequestHeader(null)

        assertThatThrownBy { aspect.handle(joinPoint, idempotent) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code", "message")
            .containsExactly(400, "Idempotency-Key header is required")

        verify(joinPoint, never()).proceed()
    }

    // ========================================================
    // 시나리오 3: PROCESSING 상태 → 409
    // ========================================================

    @Test
    @DisplayName("시나리오3: PROCESSING 상태인 key로 요청하면 409")
    fun `PROCESSING 상태이면 409 Conflict가 발생한다`() {
        setRequestHeader("Idempotency-Key", "test-key")
        whenever(idempotentManager.tryLock("test-key")).thenReturn(lock)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(idempotentManager.getStatus("test-key")).thenReturn("PROCESSING")

        assertThatThrownBy { aspect.handle(joinPoint, idempotent) }
            .isInstanceOf(GlobalHttpException::class.java)
            .extracting("code")
            .isEqualTo(409)

        verify(joinPoint, never()).proceed()
        verify(lock).unlock()
    }

    // ========================================================
    // 정상: APPROVED/null → proceed 호출
    // ========================================================

    @Test
    @DisplayName("status가 null이면 joinPoint.proceed()를 호출한다")
    fun `최초 요청이면 비즈니스 로직이 실행된다`() {
        setRequestHeader("Idempotency-Key", "new-key")
        whenever(idempotentManager.tryLock("new-key")).thenReturn(lock)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(idempotentManager.getStatus("new-key")).thenReturn(null)
        whenever(joinPoint.proceed()).thenReturn("result")

        val result = aspect.handle(joinPoint, idempotent)

        assertThat(result).isEqualTo("result")
        verify(joinPoint).proceed()
        verify(lock).unlock()
    }

    @Test
    @DisplayName("status가 APPROVED이면 joinPoint.proceed()를 호출한다")
    fun `이미 승인된 요청이면 서비스로 넘긴다`() {
        setRequestHeader("Idempotency-Key", "approved-key")
        whenever(idempotentManager.tryLock("approved-key")).thenReturn(lock)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(idempotentManager.getStatus("approved-key")).thenReturn("APPROVED")
        whenever(joinPoint.proceed()).thenReturn("cached-result")

        val result = aspect.handle(joinPoint, idempotent)

        assertThat(result).isEqualTo("cached-result")
        verify(joinPoint).proceed()
        verify(lock).unlock()
    }

    // ========================================================
    // 락 해제 보장
    // ========================================================

    @Test
    @DisplayName("비즈니스 로직 예외 발생 시에도 락이 해제된다")
    fun `예외가 발생해도 finally에서 락이 해제된다`() {
        setRequestHeader("Idempotency-Key", "error-key")
        whenever(idempotentManager.tryLock("error-key")).thenReturn(lock)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(idempotentManager.getStatus("error-key")).thenReturn(null)
        whenever(joinPoint.proceed()).thenThrow(RuntimeException("unexpected"))

        assertThatThrownBy { aspect.handle(joinPoint, idempotent) }
            .isInstanceOf(RuntimeException::class.java)

        verify(lock).unlock()
    }
}
