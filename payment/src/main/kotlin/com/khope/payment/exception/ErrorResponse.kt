package com.khope.payment.exception

import java.time.LocalDateTime

data class ErrorResponse(
    val errorCode: Int,
    val errorMessage: String,
    val timestamp: LocalDateTime
)
