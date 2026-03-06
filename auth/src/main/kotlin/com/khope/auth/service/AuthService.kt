package com.khope.auth.service

import com.khope.auth.config.JwtProvider
import com.khope.auth.domain.Account
import com.khope.auth.domain.AccountRepository
import com.khope.auth.dto.*
import com.khope.common.exception.ErrorCode
import com.khope.common.exception.NotFoundException
import com.khope.common.exception.ValidationException
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
        if (accountRepository.existsByEmail(request.email)) {
            throw ValidationException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

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
            ?: throw ValidationException(ErrorCode.INVALID_CREDENTIALS)

        if (account.password != request.password) { // TODO: BCrypt 적용
            throw ValidationException(ErrorCode.INVALID_CREDENTIALS)
        }

        return generateTokens(account)
    }

    fun refresh(request: RefreshRequest): TokenResponse {
        val userId = try {
            jwtProvider.validateToken(request.refreshToken)
        } catch (e: Exception) {
            throw ValidationException(ErrorCode.INVALID_TOKEN)
        }

        val account = accountRepository.findById(userId)
            .orElseThrow { NotFoundException(ErrorCode.USER_NOT_FOUND) }

        return generateTokens(account)
    }

    fun validate(token: String): ValidateResponse {
        val userId = try {
            jwtProvider.validateToken(token)
        } catch (e: Exception) {
            throw ValidationException(ErrorCode.INVALID_TOKEN)
        }

        val account = accountRepository.findById(userId)
            .orElseThrow { NotFoundException(ErrorCode.USER_NOT_FOUND) }

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
