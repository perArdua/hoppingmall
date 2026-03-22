package com.hoppingmall.order.config

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("OrderMetrics 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OrderMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var orderMetrics: OrderMetrics

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        orderMetrics = OrderMetrics(registry)
    }

    @Test
    fun 주문_생성_시_카운터와_금액이_증가한다() {
        orderMetrics.recordOrderCreated(BigDecimal("100000"))

        assertThat(registry.counter("order.created.count").count()).isEqualTo(1.0)
        assertThat(registry.counter("order.amount.total").count()).isEqualTo(100000.0)
    }

    @Test
    fun 주문_취소_시_카운터가_증가한다() {
        orderMetrics.recordOrderCancelled()

        assertThat(registry.counter("order.cancelled.count").count()).isEqualTo(1.0)
    }

    @Test
    fun 여러_주문_생성_시_누적_카운터가_정확하다() {
        orderMetrics.recordOrderCreated(BigDecimal("50000"))
        orderMetrics.recordOrderCreated(BigDecimal("30000"))

        assertThat(registry.counter("order.created.count").count()).isEqualTo(2.0)
        assertThat(registry.counter("order.amount.total").count()).isEqualTo(80000.0)
    }
}
