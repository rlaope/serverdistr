package com.khope.user.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

data class UserResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val createdAt: LocalDateTime,
)

data class UpdateUserRequest(
    @field:NotBlank
    val nickname: String,
    val profileImageUrl: String? = null,
)
