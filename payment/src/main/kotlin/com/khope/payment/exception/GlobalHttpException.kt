package com.khope.payment.exception

class GlobalHttpException(
    val code: Int,
    override val message: String
) : RuntimeException(message) {
}