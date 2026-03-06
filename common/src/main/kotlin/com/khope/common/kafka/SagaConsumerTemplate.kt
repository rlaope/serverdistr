package com.khope.common.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate

/**
 * Saga consumer 유틸리티.
 * action이 결과 객체를 리턴하면 resultTopic으로 전송한다.
 * 파싱 또는 action 실패 시 fallback으로 실패 응답을 전송하여 Saga가 멈추지 않도록 한다.
 */
class SagaConsumerTemplate(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T, R> execute(
        message: String,
        type: Class<T>,
        resultTopic: String,
        fallback: (String, Exception) -> R,
        action: (T) -> R,
    ) {
        val result = try {
            val event = objectMapper.readValue(message, type)
            action(event)
        } catch (e: Exception) {
            log.error("Saga consumer failed, sending fallback to $resultTopic", e)
            fallback(message, e)
        }
        val json = objectMapper.writeValueAsString(result)
        kafkaTemplate.send(resultTopic, json)
        log.info("Saga result published to $resultTopic: $json")
    }
}
