package com.khope.user.config

import com.khope.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class UserEventConsumer(
    private val userService: UserService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["user-signup"], groupId = "user-service")
    fun onUserSignup(message: String) {
        log.info("Received user-signup event: $message")
        val parts = message.split("|")
        if (parts.size == 2) {
            val userId = parts[0].toLong()
            val email = parts[1]
            userService.createFromEvent(userId, email)
        }
    }
}
