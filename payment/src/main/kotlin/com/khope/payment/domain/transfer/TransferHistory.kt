package com.khope.payment.domain.transfer

import com.khope.payment.domain.account.Account
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transfer_history")
class TransferHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val transferId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    val fromAccount: Account,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    val toAccount: Account,

    val amount: BigDecimal,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val status: TransferStatus,

    val failureReason: String? = null,

    val createdAt: LocalDateTime = LocalDateTime.now()
) {
}

enum class TransferStatus {
    SUCCESS, FAILED
}