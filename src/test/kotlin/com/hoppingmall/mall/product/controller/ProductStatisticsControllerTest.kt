package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.mall.product.dto.response.ProductStatisticsSummaryResponse
import com.hoppingmall.mall.product.exception.ProductStatisticsNotFoundException
import com.hoppingmall.mall.product.service.ProductStatisticsQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("ProductStatisticsController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductStatisticsControllerTest {

    private val productStatisticsQueryService: ProductStatisticsQueryService = mock()
    private val controller = ProductStatisticsController(productStatisticsQueryService)

    private fun createResponse(
        productId: Long = 1L,
        productName: String = "테스트 상품"
    ) = ProductStatisticsResponse(
        productId = productId,
        productName = productName,
        sellerId = 1L,
        categoryId = 1L,
        totalSalesQuantity = 100,
        totalSalesAmount = BigDecimal("1000000"),
        totalRefundQuantity = 5,
        totalRefundAmount = BigDecimal("50000"),
        currentCartCount = 10,
        currentStock = 50,
        refundRate = BigDecimal("0.0500"),
        stockTurnoverRate = BigDecimal("2.0000"),
        lastAggregatedAt = LocalDateTime.of(2026, 2, 17, 12, 0, 0)
    )

    @Nested
    @DisplayName("getAll")
    inner class GetAll {
        @Test
        fun 전체_통계_조회_성공() {
            val pageable = PageRequest.of(0, 10)
            val responses = listOf(createResponse(1L, "상품1"), createResponse(2L, "상품2"))
            val page = PageImpl(responses, pageable, responses.size.toLong())

            whenever(productStatisticsQueryService.getAll(any())).thenReturn(page)

            val result: ApiResponse<Page<ProductStatisticsResponse>> = controller.getAll(pageable)

            assertEquals("SUCCESS", result.code)
            assertEquals(2, result.data!!.content.size)
            verify(productStatisticsQueryService).getAll(pageable)
        }
    }

    @Nested
    @DisplayName("getByProductId")
    inner class GetByProductId {
        @Test
        fun 상품별_통계_조회_성공() {
            val productId = 1L
            val response = createResponse(productId)

            whenever(productStatisticsQueryService.getByProductId(productId)).thenReturn(response)

            val result: ApiResponse<ProductStatisticsResponse> = controller.getByProductId(productId)

            assertEquals("SUCCESS", result.code)
            assertEquals(productId, result.data!!.productId)
            verify(productStatisticsQueryService).getByProductId(productId)
        }

        @Test
        fun 존재하지_않는_상품_통계_조회_시_예외_발생() {
            val productId = 999L

            whenever(productStatisticsQueryService.getByProductId(productId))
                .thenThrow(ProductStatisticsNotFoundException())

            assertThrows<ProductStatisticsNotFoundException> {
                controller.getByProductId(productId)
            }

            verify(productStatisticsQueryService).getByProductId(productId)
        }
    }

    @Nested
    @DisplayName("getBySellerId")
    inner class GetBySellerId {
        @Test
        fun 판매자별_통계_조회_성공() {
            val sellerId = 1L
            val pageable = PageRequest.of(0, 10)
            val responses = listOf(createResponse(1L, "상품1"))
            val page = PageImpl(responses, pageable, responses.size.toLong())

            whenever(productStatisticsQueryService.getBySellerId(eq(sellerId), any())).thenReturn(page)

            val result: ApiResponse<Page<ProductStatisticsResponse>> = controller.getBySellerId(sellerId, pageable)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.content.size)
            verify(productStatisticsQueryService).getBySellerId(sellerId, pageable)
        }
    }

    @Nested
    @DisplayName("getByCategoryId")
    inner class GetByCategoryId {
        @Test
        fun 카테고리별_통계_조회_성공() {
            val categoryId = 1L
            val pageable = PageRequest.of(0, 10)
            val responses = listOf(createResponse(1L, "상품1"))
            val page = PageImpl(responses, pageable, responses.size.toLong())

            whenever(productStatisticsQueryService.getByCategoryId(eq(categoryId), any())).thenReturn(page)

            val result: ApiResponse<Page<ProductStatisticsResponse>> = controller.getByCategoryId(categoryId, pageable)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.content.size)
            verify(productStatisticsQueryService).getByCategoryId(categoryId, pageable)
        }
    }

    @Nested
    @DisplayName("getSummary")
    inner class GetSummary {
        @Test
        fun 전체_요약_조회_성공() {
            val summary = ProductStatisticsSummaryResponse(
                totalProductCount = 10,
                totalSalesAmount = BigDecimal("5000000"),
                totalRefundAmount = BigDecimal("250000"),
                averageRefundRate = BigDecimal("0.0500")
            )

            whenever(productStatisticsQueryService.getSummary()).thenReturn(summary)

            val result: ApiResponse<ProductStatisticsSummaryResponse> = controller.getSummary()

            assertEquals("SUCCESS", result.code)
            assertEquals(10L, result.data!!.totalProductCount)
            assertEquals(BigDecimal("5000000"), result.data!!.totalSalesAmount)
            verify(productStatisticsQueryService).getSummary()
        }
    }
}
