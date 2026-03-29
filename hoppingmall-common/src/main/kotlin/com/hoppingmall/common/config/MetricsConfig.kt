package com.hoppingmall.common.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import jakarta.annotation.PostConstruct
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
@ConditionalOnClass(MeterRegistry::class)
class MetricsConfig(
    private val registry: MeterRegistry
) {
    @PostConstruct
    fun bindMetrics() {
        JvmMemoryMetrics().bindTo(registry)
        JvmGcMetrics().bindTo(registry)
        JvmThreadMetrics().bindTo(registry)
        ClassLoaderMetrics().bindTo(registry)
        ProcessorMetrics().bindTo(registry)
    }

    @Bean
    fun httpMetricsFilter(): FilterRegistrationBean<*> {
        val filter = object : OncePerRequestFilter() {
            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                filterChain: FilterChain
            ) {
                if (request.requestURI.startsWith("/actuator")) {
                    filterChain.doFilter(request, response)
                    return
                }
                val sample = Timer.start(registry)
                try {
                    filterChain.doFilter(request, response)
                } finally {
                    sample.stop(
                        Timer.builder("http.server.requests")
                            .tag("method", request.method)
                            .tag("uri", normalizeUri(request.requestURI))
                            .tag("status", response.status.toString())
                            .tag("outcome", outcomeOf(response.status))
                            .publishPercentileHistogram()
                            .register(registry)
                    )
                }
            }

            private fun normalizeUri(uri: String): String {
                return uri.replace(Regex("/\\d+"), "/{id}")
            }

            private fun outcomeOf(status: Int): String = when {
                status < 200 -> "INFORMATIONAL"
                status < 300 -> "SUCCESS"
                status < 400 -> "REDIRECTION"
                status < 500 -> "CLIENT_ERROR"
                else -> "SERVER_ERROR"
            }
        }
        val registration = FilterRegistrationBean(filter)
        registration.order = Ordered.HIGHEST_PRECEDENCE + 1
        registration.addUrlPatterns("/*")
        return registration
    }
}
