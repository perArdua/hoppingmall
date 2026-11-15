package com.hoppingmall.product.review.port

import com.hoppingmall.product.review.exception.OrderQueryUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.time.Duration

@DisplayName("HttpOrderQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpOrderQueryAdapterTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    private lateinit var adapter: HttpOrderQueryAdapter

    @BeforeEach
    fun setUp() {
        val restTemplateBuilder = object : RestTemplateBuilder() {
            override fun connectTimeout(connectTimeout: Duration): RestTemplateBuilder = this
            override fun readTimeout(readTimeout: Duration): RestTemplateBuilder = this
            override fun build(): RestTemplate = restTemplate
        }
        adapter = HttpOrderQueryAdapter("http://localhost:8084", restTemplateBuilder)
    }

    @Test
    fun 주문_상품을_조회한다() {
        val info = OrderItemInfo(
            id = 1L, orderId = 10L, sellerId = 1L, productId = 1L,
            productName = "테스트", productPrice = BigDecimal("10000"),
            quantity = 1, totalPrice = BigDecimal("10000")
        )

        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/order-items/1",
                OrderItemInfo::class.java
            )
        ).thenReturn(info)

        val result = adapter.findOrderItemById(1L)

        assertThat(result).isNotNull
        assertThat(result!!.productId).isEqualTo(1L)
    }

    @Test
    fun 배송_완료_여부를_조회한다() {
        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/orders/10/delivered?buyerId=1",
                Boolean::class.java
            )
        ).thenReturn(true)

        val result = adapter.isDelivered(10L, 1L)

        assertThat(result).isTrue()
    }

    @Test
    fun 배송_완료_여부_null_응답_시_false를_반환한다() {
        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/orders/10/delivered?buyerId=1",
                Boolean::class.java
            )
        ).thenReturn(null)

        val result = adapter.isDelivered(10L, 1L)

        assertThat(result).isFalse()
    }

    @Test
    fun 주문별_상품_목록을_조회한다() {
        val items = arrayOf(
            OrderItemInfo(
                id = 1L, orderId = 10L, sellerId = 1L, productId = 1L,
                productName = "테스트", productPrice = BigDecimal("10000"),
                quantity = 1, totalPrice = BigDecimal("10000")
            )
        )

        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/orders/10/items",
                Array<OrderItemInfo>::class.java
            )
        ).thenReturn(items)

        val result = adapter.findOrderItemsByOrderId(10L)

        assertThat(result).hasSize(1)
    }

    @Test
    fun 주문별_상품_목록_null_응답_시_빈_리스트를_반환한다() {
        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/orders/10/items",
                Array<OrderItemInfo>::class.java
            )
        ).thenReturn(null)

        val result = adapter.findOrderItemsByOrderId(10L)

        assertThat(result).isEmpty()
    }

    @Test
    fun findOrderItemById_폴백_메서드는_404면_null이고_그_밖의_실패는_주문_조회_불가다() {
        val method = HttpOrderQueryAdapter::class.java.getDeclaredMethod(
            "findOrderItemByIdFallback", Long::class.java, Exception::class.java
        )
        method.isAccessible = true

        assertThat(method.invoke(adapter, 1L, notFound())).isNull()
        assertThatThrownBy { method.invoke(adapter, 1L, RuntimeException("fail")) }
            .hasCauseInstanceOf(OrderQueryUnavailableException::class.java)
    }

    @Test
    fun isDelivered_폴백_메서드는_404면_false이고_그_밖의_실패는_주문_조회_불가다() {
        val method = HttpOrderQueryAdapter::class.java.getDeclaredMethod(
            "isDeliveredFallback", Long::class.java, Long::class.java, Exception::class.java
        )
        method.isAccessible = true

        assertThat(method.invoke(adapter, 10L, 1L, notFound())).isEqualTo(false)
        assertThatThrownBy { method.invoke(adapter, 10L, 1L, RuntimeException("fail")) }
            .hasCauseInstanceOf(OrderQueryUnavailableException::class.java)
    }

    @Test
    fun findOrderItemsByOrderId_폴백_메서드는_빈_목록이_아니라_주문_조회_불가다() {
        val method = HttpOrderQueryAdapter::class.java.getDeclaredMethod(
            "findOrderItemsByOrderIdFallback", Long::class.java, Exception::class.java
        )
        method.isAccessible = true

        assertThatThrownBy { method.invoke(adapter, 10L, RuntimeException("fail")) }
            .hasCauseInstanceOf(OrderQueryUnavailableException::class.java)
    }

    private fun notFound(): HttpClientErrorException =
        HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, ByteArray(0), null)

    @Test
    fun OrderItemInfo를_생성한다() {
        val info = OrderItemInfo(
            id = 1L, orderId = 10L, sellerId = 1L, productId = 1L,
            productName = "테스트", productPrice = BigDecimal("10000"),
            quantity = 1, totalPrice = BigDecimal("10000")
        )

        assertThat(info.id).isEqualTo(1L)
        assertThat(info.orderId).isEqualTo(10L)
        assertThat(info.sellerId).isEqualTo(1L)
        assertThat(info.productName).isEqualTo("테스트")
        assertThat(info.quantity).isEqualTo(1)
    }
}
