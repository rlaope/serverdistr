package com.khope.payment.domain.account

import com.khope.payment.exception.GlobalHttpException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "account")
class Account(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val userId: Long,

    @Column(nullable = false, unique = true)
    val accountNumber: String,

    var balance: BigDecimal,

    val dailyTransferLimit: BigDecimal,

    val perTransferLimit: BigDecimal,

    val status: AccountStatus,

    @Version
    val version: Long,

    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun withdraw(amount: BigDecimal): BigDecimal {
        if (balance < amount) {
            throw GlobalHttpException(400, "exceed balance")
        }

        balance -= amount
        return balance
    }

    fun addAmount(amount: BigDecimal): BigDecimal {
        balance += amount
        return balance
    }
}

enum class AccountStatus {
    ACTIVE, FROZEN, CLOSED
}