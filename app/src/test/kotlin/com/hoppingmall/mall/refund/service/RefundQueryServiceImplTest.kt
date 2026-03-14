package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.refund.domain.Refund
import com.hoppingmall.mall.refund.domain.RefundItem
import com.hoppingmall.mall.refund.domain.repository.RefundItemRepository
import com.hoppingmall.mall.refund.domain.repository.RefundRepository
import com.hoppingmall.mall.refund.exception.RefundAccessDeniedException
import com.hoppingmall.mall.refund.exception.RefundNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.*

@DisplayName("RefundQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundQueryServiceImplTest {

    private val refundRepository: RefundRepository = mock()
    private val refundItemRepository: RefundItemRepository = mock()
    private val refundQueryService = RefundQueryServiceImpl(refundRepository, refundItemRepository)

    @Nested
    @DisplayName("getRefund")
    inner class GetRefund {

        @Test
        fun `구매자가_환불_상세_조회_성공`() {
            // given
            val refundId = 1L
            val buyerId = 1L
            val refund = Refund.fixture(buyerId = buyerId, sellerId = 2L)
            val refundItem = RefundItem.fixture(refundId = refundId)

            whenever(refundRepository.findById(refundId)).thenReturn(Optional.of(refund))
            whenever(refundItemRepository.findByRefundId(refundId)).thenReturn(listOf(refundItem))

            // when
            val response = refundQueryService.getRefund(refundId, buyerId)

            // then
            assertEquals(refundId, response.id)
            assertEquals(1, response.items.size)
        }

        @Test
        fun `판매자가_환불_상세_조회_성공`() {
            // given
            val refundId = 1L
            val sellerId = 2L
            val refund = Refund.fixture(buyerId = 1L, sellerId = sellerId)
            val refundItem = RefundItem.fixture(refundId = refundId)

            whenever(refundRepository.findById(refundId)).thenReturn(Optional.of(refund))
            whenever(refundItemRepository.findByRefundId(refundId)).thenReturn(listOf(refundItem))

            // when
            val response = refundQueryService.getRefund(refundId, sellerId)

            // then
            assertEquals(refundId, response.id)
            assertEquals(1, response.items.size)
        }

        @Test
        fun `존재하지_않는_환불_조회_시_예외_발생`() {
            // given
            whenever(refundRepository.findById(999L)).thenReturn(Optional.empty())

            // when & then
            assertThrows(RefundNotFoundException::class.java) {
                refundQueryService.getRefund(999L, 1L)
            }
        }

        @Test
        fun `다른_사용자의_환불_조회_시_접근_거부`() {
            // given
            val refundId = 1L
            val refund = Refund.fixture(buyerId = 1L, sellerId = 2L)

            whenever(refundRepository.findById(refundId)).thenReturn(Optional.of(refund))

            // when & then
            assertThrows(RefundAccessDeniedException::class.java) {
                refundQueryService.getRefund(refundId, 999L)
            }
        }
    }

    @Nested
    @DisplayName("getMyRefunds")
    inner class GetMyRefunds {

        @Test
        fun `내_환불_목록_조회_성공`() {
            // given
            val buyerId = 1L
            val pageable = PageRequest.of(0, 10)
            val refund = Refund.fixture(buyerId = buyerId)
            val refundItem = RefundItem.fixture(refundId = 1L)

            whenever(refundRepository.findByBuyerId(buyerId, pageable))
                .thenReturn(PageImpl(listOf(refund), pageable, 1))
            whenever(refundItemRepository.findByRefundId(1L)).thenReturn(listOf(refundItem))

            // when
            val response = refundQueryService.getMyRefunds(buyerId, pageable)

            // then
            assertEquals(1, response.totalElements)
            assertEquals(buyerId, response.content.first().buyerId)
        }
    }

    @Nested
    @DisplayName("getSellerRefunds")
    inner class GetSellerRefunds {

        @Test
        fun `판매자_환불_요청_목록_조회_성공`() {
            // given
            val sellerId = 2L
            val pageable = PageRequest.of(0, 10)
            val refund = Refund.fixture(sellerId = sellerId)
            val refundItem = RefundItem.fixture(refundId = 1L)

            whenever(refundRepository.findBySellerId(sellerId, pageable))
                .thenReturn(PageImpl(listOf(refund), pageable, 1))
            whenever(refundItemRepository.findByRefundId(1L)).thenReturn(listOf(refundItem))

            // when
            val response = refundQueryService.getSellerRefunds(sellerId, pageable)

            // then
            assertEquals(1, response.totalElements)
            assertEquals(sellerId, response.content.first().sellerId)
        }
    }
}
