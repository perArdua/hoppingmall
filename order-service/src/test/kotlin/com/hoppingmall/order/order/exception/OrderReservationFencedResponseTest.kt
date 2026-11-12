package com.hoppingmall.order.order.exception

import com.hoppingmall.common.GlobalExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

@DisplayName("예약 fence 충돌 응답 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderReservationFencedResponseTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun 예약_fence_충돌_예외는_409와_ORD009로_응답한다() {
        val response = handler.handleBusinessException(OrderReservationFencedException())

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.code).isEqualTo("ORD009")
        assertThat(response.body?.data).isNull()
    }
}
