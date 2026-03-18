package com.hoppingmall.product.internal

import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("InternalProductStatisticsController 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InternalProductStatisticsControllerTest {

    @Mock
    private lateinit var productStatisticsCommandService: ProductStatisticsCommandService

    @InjectMocks
    private lateinit var controller: InternalProductStatisticsController

    @Test
    fun 환불_통계를_증가시킨다() {
        val productId = 1L
        val quantity = 3L
        val amount = BigDecimal("15000")

        val response = controller.incrementRefundStats(productId, quantity, amount)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(productStatisticsCommandService).incrementRefundStats(productId, quantity, amount)
    }
}
