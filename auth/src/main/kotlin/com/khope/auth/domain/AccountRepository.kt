package com.khope.auth.domain

import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository : JpaRepository<Account, Long> {
    fun findByEmail(email: String): Account?
    fun existsByEmail(email: String): Boolean
}
