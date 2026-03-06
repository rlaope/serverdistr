package com.khope.common.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.util.backoff.FixedBackOff

@Configuration
@ConditionalOnClass(name = ["org.springframework.kafka.core.KafkaTemplate"])
class KafkaErrorAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun kafkaErrorHandler(kafkaTemplate: KafkaTemplate<*, *>): CommonErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate)

        // 3번 재시도, 1초 간격, 그래도 실패하면 DLT로
        val errorHandler = DefaultErrorHandler(recoverer, FixedBackOff(1000L, 2L))

        errorHandler.setRetryListeners(object : RetryListener {
            override fun failedDelivery(record: ConsumerRecord<*, *>, ex: Exception, deliveryAttempt: Int) {
                log.warn(
                    "Kafka consumer retry: topic={}, partition={}, offset={}, attempt={}, error={}",
                    record.topic(), record.partition(), record.offset(), deliveryAttempt, ex.message
                )
            }
        })

        return errorHandler
    }
}
