package com.khope.product

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@EnableCaching
@SpringBootApplication
class ProductApplication

fun main(args: Array<String>) {
    runApplication<ProductApplication>(*args)
}
