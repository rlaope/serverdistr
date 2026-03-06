package com.khope.common.health

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class HealthCheckAutoConfiguration {

    @Bean
    fun healthCheckFilterRegistration(): FilterRegistrationBean<HealthCheckFilter> {
        val registration = FilterRegistrationBean<HealthCheckFilter>()
        registration.filter = HealthCheckFilter()
        registration.addUrlPatterns("/ping")
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}

class HealthCheckFilter : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        if (httpRequest.requestURI == "/ping") {
            val httpResponse = response as HttpServletResponse
            httpResponse.status = 200
            httpResponse.contentType = "text/plain"
            httpResponse.writer.write("pong")
            return
        }
        chain.doFilter(request, response)
    }
}
