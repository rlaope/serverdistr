package com.khope.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class JwtAuthFilter(
    @Value("\${jwt.secret}") private val secret: String,
) : GatewayFilter {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val authHeader = exchange.request.headers.getFirst("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        val token = authHeader.substring(7)
        return try {
            val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            val mutatedRequest = exchange.request.mutate()
                .header("X-User-Id", claims.subject)
                .header("X-User-Role", claims["role"] as? String ?: "USER")
                .build()

            chain.filter(exchange.mutate().request(mutatedRequest).build())
        } catch (e: Exception) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            exchange.response.setComplete()
        }
    }
}
