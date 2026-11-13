package com.hoppingmall.order.grpc

import com.hoppingmall.order.port.HttpInventoryCommandAdapter
import com.hoppingmall.product.grpc.ProductQueryServiceGrpc
import com.hoppingmall.product.grpc.ProductResponse
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException

@DisplayName("gRPC 상품 조회 재시도와 재고 확정 재시도 분리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(classes = [ProductQueryRetrySeparationTest.TestConfig::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ImportAutoConfiguration(AopAutoConfiguration::class, CircuitBreakerAutoConfiguration::class, RetryAutoConfiguration::class)
class ProductQueryRetrySeparationTest {

    @Autowired
    private lateinit var productQueryAdapter: GrpcProductQueryAdapter

    @Autowired
    private lateinit var inventoryCommandAdapter: HttpInventoryCommandAdapter

    @Autowired
    private lateinit var stub: ProductQueryServiceGrpc.ProductQueryServiceBlockingStub

    @Autowired
    private lateinit var productServer: MockServerRestTemplateCustomizer

    @Autowired
    private lateinit var retryRegistry: RetryRegistry

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @BeforeEach
    fun setUp() {
        Mockito.reset(stub)
        productServer.server.reset()
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
    }

    @Test
    fun gRPC_상품_조회가_UNAVAILABLE로_한_번_실패하면_다시_조회해_상품을_돌려준다() {
        whenever(stub.findProductById(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(ProductResponse.newBuilder().setId(PRODUCT_ID).setName("상품A").setPrice("5000.00").setSellerId(2L).build())

        val product = productQueryAdapter.findProductById(PRODUCT_ID)

        assertThat(product!!.id).isEqualTo(PRODUCT_ID)
        verify(stub, times(2)).findProductById(any())
    }

    @Test
    fun gRPC_상품_조회가_DEADLINE_EXCEEDED로_한_번_실패하면_다시_조회한다() {
        whenever(stub.findProductById(any()))
            .thenThrow(StatusRuntimeException(Status.DEADLINE_EXCEEDED))
            .thenReturn(ProductResponse.newBuilder().setId(PRODUCT_ID).setName("상품A").setPrice("5000.00").setSellerId(2L).build())

        val product = productQueryAdapter.findProductById(PRODUCT_ID)

        assertThat(product!!.id).isEqualTo(PRODUCT_ID)
        verify(stub, times(2)).findProductById(any())
    }

    @Test
    fun gRPC_상품_조회가_NOT_FOUND면_다시_조회하지_않고_없음으로_돌려준다() {
        whenever(stub.findProductById(any())).thenThrow(StatusRuntimeException(Status.NOT_FOUND))

        val product = productQueryAdapter.findProductById(PRODUCT_ID)

        assertThat(product).isNull()
        verify(stub, times(1)).findProductById(any())
    }

    @Test
    fun 재고_확정의_I_O_재시도는_상품_조회_재시도_인스턴스를_쓰지_않는다() {
        val queryRetriesBefore = retryRegistry.retry(PRODUCT_QUERY).metrics.numberOfSuccessfulCallsWithRetryAttempt
        productServer.server.expect(requestTo("$PRODUCT_URL/internal/api/v1/inventory/reservations/batch-confirm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withException(IOException("connection reset")))
        productServer.server.expect(requestTo("$PRODUCT_URL/internal/api/v1/inventory/reservations/batch-confirm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"confirmed":true}""", MediaType.APPLICATION_JSON))

        val confirmed = inventoryCommandAdapter.confirmReservations(listOf("rsv-1"))

        assertThat(confirmed).isTrue()
        productServer.server.verify()
        assertThat(retryRegistry.retry(PRODUCT_QUERY).metrics.numberOfSuccessfulCallsWithRetryAttempt)
            .isEqualTo(queryRetriesBefore)
    }

    @Configuration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun productQueryStub(): ProductQueryServiceGrpc.ProductQueryServiceBlockingStub = mock()

        @Bean
        fun grpcProductQueryAdapter(stub: ProductQueryServiceGrpc.ProductQueryServiceBlockingStub): GrpcProductQueryAdapter =
            GrpcProductQueryAdapter(stub)

        @Bean
        fun mockServerRestTemplateCustomizer(): MockServerRestTemplateCustomizer = MockServerRestTemplateCustomizer()

        @Bean
        fun httpInventoryCommandAdapter(customizer: MockServerRestTemplateCustomizer): HttpInventoryCommandAdapter =
            HttpInventoryCommandAdapter(PRODUCT_URL, RestTemplateBuilder(customizer))
    }

    companion object {
        private const val PRODUCT_ID = 1L
        private const val PRODUCT_QUERY = "product-query"
        private const val PRODUCT_URL = "http://product-service"
    }
}
