package com.khope.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@EnableCaching
@SpringBootApplication
class PaymentApplication

fun main(args: Array<String>) {
    runApplication<PaymentApplication>(*args)
}