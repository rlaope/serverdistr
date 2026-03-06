package com.khope.user.controller

import com.khope.user.dto.UpdateUserRequest
import com.khope.user.dto.UserResponse
import com.khope.user.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.findById(id))
    }

    @GetMapping("/me")
    fun getMe(@RequestHeader("X-User-Id") userId: Long): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.findById(userId))
    }

    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserRequest,
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.update(id, request))
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        userService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
