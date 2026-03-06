package com.khope.auth.service

import com.khope.auth.config.JwtProvider
import com.khope.auth.domain.Account
import com.khope.auth.domain.AccountRepository
import com.khope.auth.dto.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
class AuthService(
    private val accountRepository: AccountRepository,
    private val jwtProvider: JwtProvider,
    private val redisTemplate: StringRedisTemplate,
) {

    @Transactional
    fun signup(request: SignupRequest): TokenResponse {
        require(!accountRepository.existsByEmail(request.email)) { "Email already exists" }

        val account = accountRepository.save(
            Account(
                email = request.email,
                password = request.password, // TODO: BCrypt 적용
                role = com.khope.auth.domain.Role.USER,
            )
        )
        return generateTokens(account)
    }

    fun login(request: LoginRequest): TokenResponse {
        val account = accountRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Invalid email or password")

        require(account.password == request.password) { "Invalid email or password" } // TODO: BCrypt 적용

        return generateTokens(account)
    }

    fun refresh(request: RefreshRequest): TokenResponse {
        val userId = jwtProvider.validateToken(request.refreshToken)
        val account = accountRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }

        return generateTokens(account)
    }

    fun validate(token: String): ValidateResponse {
        val userId = jwtProvider.validateToken(token)
        val account = accountRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }

        return ValidateResponse(
            userId = account.id,
            email = account.email,
            role = account.role.name,
        )
    }

    private fun generateTokens(account: Account): TokenResponse {
        val accessToken = jwtProvider.generateAccessToken(account.id, account.email, account.role.name)
        val refreshToken = jwtProvider.generateRefreshToken(account.id)

        redisTemplate.opsForValue().set(
            "refresh:${account.id}",
            refreshToken,
            7, TimeUnit.DAYS,
        )

        return TokenResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
