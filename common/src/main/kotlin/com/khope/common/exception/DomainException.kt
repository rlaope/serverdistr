package com.khope.common.exception

open class DomainException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)

class NotFoundException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
) : DomainException(errorCode, message)

class ValidationException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
) : DomainException(errorCode, message)

class AccessDeniedException(
    errorCode: ErrorCode = ErrorCode.ACCESS_DENIED,
    message: String = errorCode.message,
) : DomainException(errorCode, message)
