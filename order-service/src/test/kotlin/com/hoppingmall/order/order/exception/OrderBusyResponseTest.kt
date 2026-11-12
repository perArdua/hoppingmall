package com.hoppingmall.order.order.exception

import com.hoppingmall.common.GlobalExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

@DisplayName("주문 혼잡 응답 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderBusyResponseTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun 주문_혼잡_예외는_429와_ORD008로_응답한다() {
        val response = handler.handleBusinessException(OrderBusyException())

        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(response.body?.code).isEqualTo("ORD008")
        assertThat(response.body?.data).isNull()
    }

    @Test
    fun 재고_부족_예외는_여전히_400과_ORD007로_응답한다() {
        val response = handler.handleBusinessException(OrderOutOfStockException())

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo("ORD007")
    }
}
