package com.khope.product.config

import com.khope.product.domain.Product
import com.khope.product.domain.ProductRepository
import com.khope.product.domain.ProductStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DataInitializer(
    private val productRepository: ProductRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (productRepository.count() == 0L) {
            val products = listOf(
                Product(name = "Kotlin in Action", description = "코틀린 프로그래밍 입문서", price = BigDecimal(35000), stock = 100, status = ProductStatus.ACTIVE),
                Product(name = "Spring Boot 실전", description = "스프링 부트로 배우는 백엔드", price = BigDecimal(42000), stock = 50, status = ProductStatus.ACTIVE),
                Product(name = "마이크로서비스 패턴", description = "분산 시스템 설계 가이드", price = BigDecimal(38000), stock = 30, status = ProductStatus.ACTIVE),
                Product(name = "도메인 주도 설계", description = "DDD 핵심 패턴과 실전", price = BigDecimal(45000), stock = 20, status = ProductStatus.ACTIVE),
                Product(name = "카프카 핵심 가이드", description = "Apache Kafka 완벽 가이드", price = BigDecimal(33000), stock = 0, status = ProductStatus.SOLD_OUT),
            )
            productRepository.saveAll(products)
            log.info("Sample products created: ${products.size} items")
        }
    }
}
