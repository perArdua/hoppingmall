package com.hoppingmall.common

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.support.StaticApplicationContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@DisplayName("서킷 브레이커 호출 거절 응답 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CircuitBreakerExceptionHandlerTest {

    private val registry = CircuitBreakerRegistry.ofDefaults().apply {
        circuitBreaker(
            "inventory-command",
            CircuitBreakerConfig.custom().waitDurationInOpenState(Duration.ofSeconds(30)).build()
        )
        circuitBreaker(
            "order-query",
            CircuitBreakerConfig.custom().waitDurationInOpenState(Duration.ofMillis(9_500)).build()
        )
    }

    @Test
    fun 서킷이_호출을_거절하면_503과_S003을_응답하고_열림_대기만큼_Retry_After를_준다() {
        mockMvc(providerOf(registry)).perform(get("/circuit-breaker/inventory-command"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(jsonPath("$.code").value("S003"))
            .andExpect(jsonPath("$.message").value(CommonErrorCode.DEPENDENCY_UNAVAILABLE.message))
    }

    @Test
    fun 열림_대기가_초_단위로_나누어떨어지지_않으면_올림한_초를_준다() {
        mockMvc(providerOf(registry)).perform(get("/circuit-breaker/order-query"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Retry-After", "10"))
    }

    @Test
    fun 서킷_레지스트리가_없으면_기본_10초를_Retry_After로_준다() {
        mockMvc(providerOf(null)).perform(get("/circuit-breaker/inventory-command"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Retry-After", "10"))
            .andExpect(jsonPath("$.code").value("S003"))
    }

    @Test
    fun 서킷_레지스트리에_없는_서킷이면_기본_10초를_Retry_After로_준다() {
        val otherRegistry = CircuitBreakerRegistry.ofDefaults()

        mockMvc(providerOf(otherRegistry)).perform(get("/circuit-breaker/inventory-command"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Retry-After", "10"))
    }

    @Test
    fun 원인_예외_사슬에_호출_거절이_있으면_503과_S003을_응답한다() {
        mockMvc(providerOf(registry)).perform(get("/circuit-breaker/inventory-command/wrapped"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(jsonPath("$.code").value("S003"))
    }

    @Test
    fun 클래스패스에_resilience4j가_있을_때만_핸들러가_등록된다() {
        val contextRunner = ApplicationContextRunner()
            .withUserConfiguration(CircuitBreakerExceptionHandler::class.java)

        contextRunner.run { context ->
            assertThat(context).hasSingleBean(CircuitBreakerExceptionHandler::class.java)
        }
        contextRunner.withClassLoader(FilteredClassLoader(CallNotPermittedException::class.java)).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(CircuitBreakerExceptionHandler::class.java)
        }
    }

    private fun mockMvc(provider: ObjectProvider<CircuitBreakerRegistry>): MockMvc =
        MockMvcBuilders.standaloneSetup(CircuitBreakerTestController(registry))
            .setControllerAdvice(GlobalExceptionHandler(), CircuitBreakerExceptionHandler(provider))
            .build()

    private fun providerOf(registry: CircuitBreakerRegistry?): ObjectProvider<CircuitBreakerRegistry> {
        val context = StaticApplicationContext()
        registry?.let { context.beanFactory.registerSingleton("circuitBreakerRegistry", it) }
        return context.getBeanProvider(CircuitBreakerRegistry::class.java)
    }

    @RestController
    private class CircuitBreakerTestController(private val registry: CircuitBreakerRegistry) {
        @GetMapping("/circuit-breaker/{name}")
        fun call(@PathVariable("name") name: String): String = throw callNotPermitted(name)

        @GetMapping("/circuit-breaker/{name}/wrapped")
        fun wrapped(@PathVariable("name") name: String): String = throw RuntimeException("wrapped", callNotPermitted(name))

        private fun callNotPermitted(name: String): CallNotPermittedException {
            val circuitBreaker = registry.circuitBreaker(name)
            circuitBreaker.transitionToOpenState()
            return CallNotPermittedException.createCallNotPermittedException(circuitBreaker)
        }
    }
}
