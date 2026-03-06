package com.khope.gateway.health

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class HealthCheckWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (exchange.request.uri.path == "/ping") {
            val response = exchange.response
            response.statusCode = HttpStatus.OK
            response.headers.contentType = MediaType.TEXT_PLAIN
            val buffer = response.bufferFactory().wrap("pong".toByteArray())
            return response.writeWith(Mono.just(buffer))
        }
        return chain.filter(exchange)
    }
}
