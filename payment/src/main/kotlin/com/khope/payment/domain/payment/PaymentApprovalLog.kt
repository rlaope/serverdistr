package com.khope.payment.domain.payment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment_approval_log")
class PaymentApprovalLog(
    /**
     * id	Long	PK
     * paymentApprovalId	Long	FK
     * action	String	REQUESTED / APPROVED / FAILED / DUPLICATE_REQUESTED
     * detail	String?	상세 내용
     * createdAt	LocalDateTime
     *
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "payment_id")
    val paymentApproval: PaymentApproval,

    @Column
    @Enumerated(EnumType.STRING)
    val action: Action,

    val detail: String? = null,

    val createdAt: LocalDateTime? = null,
) {

}

enum class Action {
    REQUESTED, APPROVED, FAILED, DUPLICATE_REQUESTED
}