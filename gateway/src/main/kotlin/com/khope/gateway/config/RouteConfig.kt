package com.khope.gateway.config

import com.khope.gateway.filter.JwtAuthFilter
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RouteConfig(
    private val jwtAuthFilter: JwtAuthFilter,
) {

    @Bean
    fun routes(builder: RouteLocatorBuilder): RouteLocator = builder.routes()
        .route("auth-service") { r ->
            r.path("/api/auth/**")
                .uri("http://localhost:8081")
        }
        .route("user-service") { r ->
            r.path("/api/users/**")
                .filters { f -> f.filter(jwtAuthFilter) }
                .uri("http://localhost:8082")
        }
        .route("product-service") { r ->
            r.path("/api/products/**")
                .filters { f -> f.filter(jwtAuthFilter) }
                .uri("http://localhost:8083")
        }
        .build()
}
