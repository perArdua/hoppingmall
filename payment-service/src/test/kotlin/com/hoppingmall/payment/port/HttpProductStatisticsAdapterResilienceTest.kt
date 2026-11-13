package com.hoppingmall.payment.port

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.util.AopTestUtils
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("환불 통계 호출의 서킷과 재시도")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(
    classes = [HttpProductStatisticsAdapter::class, HttpInventoryCommandAdapter::class],
    properties = ["services.product-service.url=http://product.test"]
)
@ImportAutoConfiguration(
    AopAutoConfiguration::class,
    CircuitBreakerAutoConfiguration::class,
    RetryAutoConfiguration::class,
    RestTemplateAutoConfiguration::class
)
class HttpProductStatisticsAdapterResilienceTest {

    @Autowired
    private lateinit var productStatisticsPort: ProductStatisticsPort

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    private val statsUrl = "http://product.test/internal/api/v1/product-statistics/10/refund?quantity=2&amount=5000"

    @AfterEach
    fun resetCircuits() {
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
    }

    private fun mockServer(): MockRestServiceServer {
        val target = AopTestUtils.getTargetObject<HttpProductStatisticsAdapter>(productStatisticsPort)
        val restTemplate = ReflectionTestUtils.getField(target, "restTemplate") as RestTemplate
        return MockRestServiceServer.bindTo(restTemplate).build()
    }

    @Test
    fun 읽기_시간_초과로_실패한_통계_증가는_다시_보내지_않는다() {
        val server = mockServer()
        val requests = AtomicInteger()
        server.expect(ExpectedCount.manyTimes(), requestTo(statsUrl))
            .andRespond {
                requests.incrementAndGet()
                throw SocketTimeoutException("Read timed out")
            }

        assertThatThrownBy { productStatisticsPort.incrementRefundStats(10L, 2L, BigDecimal("5000")) }
            .isInstanceOf(Exception::class.java)

        assertThat(requests.get()).isEqualTo(1)
    }

    @Test
    fun 재고_명령_서킷이_열려_있어도_통계_증가는_보낸다() {
        val server = mockServer()
        server.expect(ExpectedCount.once(), requestTo(statsUrl)).andRespond(withSuccess())
        circuitBreakerRegistry.circuitBreaker("inventory-command").transitionToForcedOpenState()

        productStatisticsPort.incrementRefundStats(10L, 2L, BigDecimal("5000"))

        server.verify()
    }

    @Test
    fun 통계_증가_실패는_재고_명령_서킷에_기록되지_않는다() {
        val server = mockServer()
        server.expect(ExpectedCount.times(5), requestTo(statsUrl)).andRespond(withServerError())

        repeat(5) {
            assertThatThrownBy { productStatisticsPort.incrementRefundStats(10L, 2L, BigDecimal("5000")) }
                .isInstanceOf(Exception::class.java)
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("inventory-command").metrics.numberOfFailedCalls).isZero()
    }
}
