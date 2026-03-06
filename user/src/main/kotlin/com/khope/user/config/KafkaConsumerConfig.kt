package com.khope.user.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.khope.common.event.UserSignupEvent
import com.khope.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class UserEventConsumer(
    private val userService: UserService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["user-signup"], groupId = "user-service")
    fun onUserSignup(message: String) {
        log.info("Received user-signup event: $message")
        val event = objectMapper.readValue(message, UserSignupEvent::class.java)
        userService.createFromEvent(event.userId, event.email)
    }
}
