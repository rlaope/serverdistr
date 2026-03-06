package com.khope.user.controller

import com.khope.common.web.UserId
import com.khope.user.client.OrderPageResponse
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
    fun getMe(@UserId userId: Long): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.findById(userId))
    }

    @GetMapping("/me/orders")
    fun getMyOrders(
        @UserId userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<OrderPageResponse> {
        return ResponseEntity.ok(userService.getMyOrders(userId, page, size))
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
