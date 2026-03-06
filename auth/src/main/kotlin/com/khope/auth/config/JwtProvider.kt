package com.khope.auth.config

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class JwtProvider(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-expiration}") private val accessExpiration: Long,
    @Value("\${jwt.refresh-expiration}") private val refreshExpiration: Long,
) {
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)) }

    fun generateAccessToken(userId: Long, email: String, role: String): String {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + accessExpiration))
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(userId: Long): String {
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpiration))
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Long {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        return claims.subject.toLong()
    }
}
