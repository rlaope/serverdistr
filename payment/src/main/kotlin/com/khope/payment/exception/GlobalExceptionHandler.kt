package com.khope.payment.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(GlobalHttpException::class)
    fun handleGlobalHttpException(ex: GlobalHttpException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            errorCode = ex.code,
            errorMessage= ex.message,
            timestamp = LocalDateTime.now(),
        )

        return ResponseEntity
            .status(ex.code)
            .body(errorResponse)
    }

    @ExceptionHandler(Throwable::class)
    fun handleThrowable(ex: Throwable): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            errorCode = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            errorMessage = "Internal Server Error",
            timestamp = LocalDateTime.now(),
        )

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .body(errorResponse)
    }
}