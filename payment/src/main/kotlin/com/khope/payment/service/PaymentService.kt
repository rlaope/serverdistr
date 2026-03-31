package com.khope.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.khope.payment.controller.PaymentController.ApproveRequest
import com.khope.payment.controller.PaymentController.ApproveResponse
import com.khope.payment.domain.Action
import com.khope.payment.domain.Payment
import com.khope.payment.domain.PaymentApproval
import com.khope.payment.domain.PaymentApprovalLog
import com.khope.payment.domain.PaymentApprovalLogRepository
import com.khope.payment.domain.PaymentApprovalRepository
import com.khope.payment.domain.PaymentRepository
import com.khope.payment.domain.PaymentStatus
import com.khope.payment.exception.GlobalHttpException
import com.khope.payment.util.IdempotentManager
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentApprovalRepository: PaymentApprovalRepository,
    private val paymentApprovalLogRepository: PaymentApprovalLogRepository,
    private val idempotentManager: IdempotentManager,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun approvePayment(idempotencyKey: String, request: ApproveRequest): ApproveResponse {
        val requestHash = idempotentManager.hashRequest(request)
        val status = idempotentManager.getStatus(idempotencyKey)

        if (status == "APPROVED" || status == "FAILED") {
            return handleDuplicateRequest(idempotencyKey, requestHash)
        }

        return handleFirstRequest(idempotencyKey, requestHash, request)
    }

    private fun handleDuplicateRequest(idempotencyKey: String, requestHash: String): ApproveResponse {
        validateRequestHash(idempotencyKey, requestHash)
        logDuplicateRequest(idempotencyKey)

        val cached = idempotentManager.getCachedResponse(idempotencyKey)
            ?: throw GlobalHttpException(500, "Cached response not found")

        return objectMapper.readValue(cached, ApproveResponse::class.java)
    }

    private fun handleFirstRequest(
        idempotencyKey: String,
        requestHash: String,
        request: ApproveRequest
    ): ApproveResponse {
        idempotentManager.markProcessing(idempotencyKey, requestHash)

        try {
            val payment = findPayment(request.orderId)
            validatePaymentCanApproved(payment, request)
            val approval = processPaymentApprove(idempotencyKey, payment, request)

            val response = toApproveResponse(payment, approval)
            idempotentManager.markCompleted(idempotencyKey, "APPROVED", objectMapper.writeValueAsString(response))

            return response
        } catch (e: Exception) {
            idempotentManager.cleanup(idempotencyKey)
            throw e
        }
    }

    private fun validateRequestHash(idempotencyKey: String, requestHash: String) {
        val storedHash = idempotentManager.getRequestHash(idempotencyKey)
        if (storedHash != null && storedHash != requestHash) {
            throw GlobalHttpException(422, "Request body does not match the original request for this idempotency key")
        }
    }

    private fun logDuplicateRequest(idempotencyKey: String) {
        val approval = paymentApprovalRepository.findByIdempotencyKey(idempotencyKey) ?: return
        paymentApprovalLogRepository.save(
            PaymentApprovalLog(
                paymentApproval = approval,
                action = Action.DUPLICATE_REQUESTED,
                detail = "duplicate request",
                createdAt = LocalDateTime.now()
            )
        )
    }

    private fun findPayment(orderId: String): Payment {
        return paymentRepository.findByOrderId(orderId)
            ?: throw GlobalHttpException(404, "Payment not found")
    }

    private fun validatePaymentCanApproved(payment: Payment, request: ApproveRequest) {
        if (payment.amount != request.amount) {
            throw GlobalHttpException(400, "Amount does not match")
        }
        if (request.paymentMethod == "CASH") {
            throw GlobalHttpException(400, "CASH payment method is not supported")
        }
    }

    private fun processPaymentApprove(
        idempotencyKey: String,
        payment: Payment,
        request: ApproveRequest
    ): PaymentApproval {
        Thread.sleep(500)

        val approval = paymentApprovalRepository.save(
            PaymentApproval(
                idempotencyKey = idempotencyKey,
                payment = payment,
                orderId = request.orderId,
                amount = request.amount,
                status = PaymentStatus.APPROVED,
                approvedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now()
            )
        )

        paymentApprovalLogRepository.save(
            PaymentApprovalLog(
                paymentApproval = approval,
                action = Action.APPROVED,
                detail = "approve",
                createdAt = LocalDateTime.now()
            )
        )

        return approval
    }

    private fun toApproveResponse(payment: Payment, approval: PaymentApproval): ApproveResponse {
        return ApproveResponse(
            paymentId = payment.id,
            orderId = approval.orderId,
            amount = payment.amount,
            status = approval.status,
            approvedAt = approval.approvedAt!!,
            message = "Payment approved successfully"
        )
    }
}
