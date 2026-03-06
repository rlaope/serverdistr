package com.khope.product.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KafkaConfig {

    @Bean
    fun productCreatedTopic(): NewTopic = NewTopic("product-created", 1, 1)

    @Bean
    fun productUpdatedTopic(): NewTopic = NewTopic("product-updated", 1, 1)
}
