package com.hoppingmall.payment.port

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.env.PropertiesPropertySourceLoader
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.io.ClassPathResource

@DisplayName("프로필별 주문 조회 서킷 브레이커 바인딩")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderQueryCircuitBreakerBindingTest {

    @ParameterizedTest
    @ValueSource(strings = ["", "application-docker.yml", "application-k8s.yml", "application-chaos-t10.properties", "application-chaos-t60.properties"])
    fun 주문_조회_서킷은_10초_시간_창과_최소_50번_호출로_묶이고_열림_대기는_10초다(profileFile: String) {
        contextRunner(profileFile).run { context ->
            assertThat(context).hasNotFailed()
            val config = context.getBean(CircuitBreakerRegistry::class.java).circuitBreaker(ORDER_QUERY).circuitBreakerConfig

            assertThat(config.slidingWindowType).isEqualTo(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            assertThat(config.slidingWindowSize).isEqualTo(10)
            assertThat(config.minimumNumberOfCalls).isEqualTo(50)
            assertThat(config.failureRateThreshold).isEqualTo(50f)
            assertThat(config.waitIntervalFunctionInOpenState.apply(1)).isEqualTo(10_000L)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "application-docker.yml", "application-k8s.yml", "application-chaos-t10.properties", "application-chaos-t60.properties"])
    fun 다른_서킷은_기본_설정의_횟수_기반_창을_그대로_쓴다(profileFile: String) {
        contextRunner(profileFile).run { context ->
            assertThat(context).hasNotFailed()
            val registry = context.getBean(CircuitBreakerRegistry::class.java)

            listOf("membership-query", "order-command", "inventory-command").forEach { name ->
                val config = registry.circuitBreaker(name).circuitBreakerConfig
                assertThat(config.slidingWindowType).describedAs(name).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                assertThat(config.slidingWindowSize).describedAs(name).isEqualTo(10)
                assertThat(config.minimumNumberOfCalls).describedAs(name).isEqualTo(5)
            }
        }
    }

    private fun contextRunner(profileFile: String): ApplicationContextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration::class.java))
            .withInitializer { context ->
                val sources = context.environment.propertySources
                YamlPropertySourceLoader().load(BASE_FILE, ClassPathResource(BASE_FILE)).forEach { sources.addLast(it) }
                if (profileFile.isNotEmpty()) {
                    val loader = if (profileFile.endsWith(".yml")) YamlPropertySourceLoader() else PropertiesPropertySourceLoader()
                    loader.load(profileFile, ClassPathResource(profileFile)).reversed().forEach { sources.addFirst(it) }
                }
            }

    companion object {
        private const val BASE_FILE = "application.yml"
        private const val ORDER_QUERY = "order-query"
    }
}
