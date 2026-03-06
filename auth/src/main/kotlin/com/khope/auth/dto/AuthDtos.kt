package com.khope.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 6, max = 100)
    val password: String,
)

data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class ValidateResponse(
    val userId: Long,
    val email: String,
    val role: String,
)
