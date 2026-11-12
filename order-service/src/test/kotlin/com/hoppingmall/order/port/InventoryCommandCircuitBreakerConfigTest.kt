package com.hoppingmall.order.port

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.util.Properties
import java.util.concurrent.TimeUnit

@DisplayName("재고 명령 서킷 브레이커 설정 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryCommandCircuitBreakerConfigTest {

    private val properties: Properties = YamlPropertiesFactoryBean()
        .apply { setResources(ClassPathResource("application.yml")) }
        .getObject()!!

    @Test
    fun 재고_명령_서킷은_기본_설정을_쓰고_404_400_429를_무시_예외로_둔다() {
        assertThat(properties.getProperty("$INVENTORY_COMMAND.baseConfig")).isEqualTo("default")
        assertThat(ignoredExceptionNames()).containsExactlyInAnyOrder(
            HttpClientErrorException.NotFound::class.java.name,
            HttpClientErrorException.BadRequest::class.java.name,
            HttpClientErrorException.TooManyRequests::class.java.name
        )
    }

    @Test
    fun 설정된_무시_예외로_만든_서킷은_429를_창_크기만큼_연속으로_받아도_닫혀_있다() {
        val circuitBreaker = circuitBreakerFromYaml()

        repeat(windowSize()) {
            circuitBreaker.onError(100, TimeUnit.MILLISECONDS, clientError(HttpStatus.TOO_MANY_REQUESTS))
        }

        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isZero()
    }

    @Test
    fun 같은_서킷은_5xx를_창_크기만큼_연속으로_받으면_열린다() {
        val circuitBreaker = circuitBreakerFromYaml()

        repeat(windowSize()) {
            circuitBreaker.onError(
                100, TimeUnit.MILLISECONDS,
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "error", HttpHeaders.EMPTY, ByteArray(0), null)
            )
        }

        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.OPEN)
    }

    private fun circuitBreakerFromYaml(): CircuitBreaker {
        @Suppress("UNCHECKED_CAST")
        val ignored = ignoredExceptionNames().map { Class.forName(it) as Class<out Throwable> }.toTypedArray()
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(windowSize())
            .minimumNumberOfCalls(intProperty("minimumNumberOfCalls"))
            .failureRateThreshold(intProperty("failureRateThreshold").toFloat())
            .ignoreExceptions(*ignored)
            .build()
        return CircuitBreakerRegistry.of(config).circuitBreaker("inventory-command")
    }

    private fun windowSize(): Int = intProperty("slidingWindowSize")

    private fun intProperty(name: String): Int =
        properties.getProperty("$DEFAULT_CONFIG.$name").toInt()

    private fun ignoredExceptionNames(): List<String> =
        generateSequence(0) { it + 1 }
            .map { properties.getProperty("$INVENTORY_COMMAND.ignoreExceptions[$it]") }
            .takeWhile { it != null }
            .map { it!! }
            .toList()

    private fun clientError(status: HttpStatus): HttpClientErrorException =
        HttpClientErrorException.create(status, "error", HttpHeaders.EMPTY, ByteArray(0), null)

    companion object {
        private const val DEFAULT_CONFIG = "resilience4j.circuitbreaker.configs.default"
        private const val INVENTORY_COMMAND = "resilience4j.circuitbreaker.instances.inventory-command"
    }
}
