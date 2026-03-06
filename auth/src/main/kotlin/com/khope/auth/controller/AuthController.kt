package com.khope.auth.controller

import com.khope.auth.dto.*
import com.khope.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(authService.login(request))
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(authService.refresh(request))
    }

    @GetMapping("/validate")
    fun validate(@RequestHeader("Authorization") authorization: String): ResponseEntity<ValidateResponse> {
        val token = authorization.removePrefix("Bearer ")
        return ResponseEntity.ok(authService.validate(token))
    }
}
