package com.khope.common.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnClass(name = ["org.springframework.kafka.core.KafkaTemplate"])
class DltConsumer {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topicPattern = ".*\\.DLT", groupId = "dlt-consumer")
    fun onDltMessage(record: ConsumerRecord<String, String>) {
        log.error(
            "[DLT] Failed message received - topic: {}, originalTopic: {}, key: {}, value: {}, partition: {}, offset: {}",
            record.topic(),
            record.topic().removeSuffix(".DLT"),
            record.key(),
            record.value(),
            record.partition(),
            record.offset(),
        )
    }
}
