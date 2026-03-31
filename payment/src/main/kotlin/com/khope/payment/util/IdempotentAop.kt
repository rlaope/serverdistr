package com.khope.payment.util

import com.khope.payment.exception.GlobalHttpException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Aspect
@Component
class IdempotentAop(
    private val idempotentManager: IdempotentManager
) {

    @Around("@annotation(idempotent)")
    fun handle(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        val key = extractIdempotencyKey()
        val lock = idempotentManager.tryLock(key)

        try {
            val status = idempotentManager.getStatus(key)
            if (status == "PROCESSING") {
                throw GlobalHttpException(409, "Payment is currently being processed with this idempotency key")
            }

            return joinPoint.proceed()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    private fun extractIdempotencyKey(): String {
        val request = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
        return request.getHeader("Idempotency-Key")
            ?: throw GlobalHttpException(400, "Idempotency-Key header is required")
    }
}
