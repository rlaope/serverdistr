package com.khope.user.service

import com.khope.common.exception.ErrorCode
import com.khope.common.exception.NotFoundException
import com.khope.user.client.OrderClient
import com.khope.user.client.OrderPageResponse
import com.khope.user.domain.User
import com.khope.user.domain.UserRepository
import com.khope.user.dto.UpdateUserRequest
import com.khope.user.dto.UserResponse
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserService(
    private val userRepository: UserRepository,
    private val orderClient: OrderClient,
) {

    @Cacheable(value = ["users"], key = "#id")
    fun findById(id: Long): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: $id") }
        return user.toResponse()
    }

    fun getMyOrders(userId: Long, page: Int, size: Int): OrderPageResponse {
        findById(userId)
        return orderClient.getOrdersByUserId(userId, page, size)
            ?: throw IllegalStateException("Failed to fetch orders from order service")
    }

    @CachePut(value = ["users"], key = "#id")
    @Transactional
    fun update(id: Long, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: $id") }

        user.nickname = request.nickname
        user.profileImageUrl = request.profileImageUrl
        user.updatedAt = LocalDateTime.now()

        return userRepository.save(user).toResponse()
    }

    @CacheEvict(value = ["users"], key = "#id")
    @Transactional
    fun delete(id: Long) {
        if (!userRepository.existsById(id)) {
            throw NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: $id")
        }
        userRepository.deleteById(id)
    }

    @Transactional
    fun createFromEvent(userId: Long, email: String) {
        if (!userRepository.existsById(userId)) {
            userRepository.save(
                User(
                    id = userId,
                    nickname = email.substringBefore("@"),
                )
            )
        }
    }

    private fun User.toResponse() = UserResponse(
        id = id,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
        createdAt = createdAt,
    )
}
