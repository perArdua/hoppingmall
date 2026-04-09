package com.hoppingmall.order.refund.service

import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.RefundItem
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.exception.RefundAccessDeniedException
import com.hoppingmall.order.refund.exception.RefundNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("RefundQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundQueryServiceImplTest {

    @Mock
    private lateinit var refundRepository: RefundRepository

    @Mock
    private lateinit var refundItemRepository: RefundItemRepository

    @InjectMocks
    private lateinit var service: RefundQueryServiceImpl

    private fun createRefund(id: Long = 1L, buyerId: Long = 1L, sellerId: Long = 5L): Refund {
        val refund = Refund.create(
            orderId = 10L, paymentId = 20L, buyerId = buyerId, sellerId = sellerId,
            reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            refundAmount = BigDecimal("10000"), isFullRefund = false
        )
        ReflectionTestUtils.setField(refund, "id", id)
        return refund
    }

    private fun createRefundItem(id: Long = 1L, refundId: Long = 1L): RefundItem {
        val item = RefundItem.create(
            refundId = refundId, orderItemId = 10L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("10000"), quantity = 1
        )
        ReflectionTestUtils.setField(item, "id", id)
        return item
    }

    @Test
    fun 구매자가_환불을_조회한다() {
        val refund = createRefund()
        val items = listOf(createRefundItem())

        whenever(refundRepository.findById(1L)).thenReturn(Optional.of(refund))
        whenever(refundItemRepository.findByRefundId(1L)).thenReturn(items)

        val result = service.getRefund(1L, 1L)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.items).hasSize(1)
    }

    @Test
    fun 판매자가_환불을_조회한다() {
        val refund = createRefund()
        val items = listOf(createRefundItem())

        whenever(refundRepository.findById(1L)).thenReturn(Optional.of(refund))
        whenever(refundItemRepository.findByRefundId(1L)).thenReturn(items)

        val result = service.getRefund(1L, 5L)

        assertThat(result.id).isEqualTo(1L)
    }

    @Test
    fun 관련없는_사용자가_환불_조회_시_예외가_발생한다() {
        val refund = createRefund()

        whenever(refundRepository.findById(1L)).thenReturn(Optional.of(refund))

        assertThatThrownBy { service.getRefund(1L, 999L) }
            .isInstanceOf(RefundAccessDeniedException::class.java)
    }

    @Test
    fun 존재하지_않는_환불_조회_시_예외가_발생한다() {
        whenever(refundRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.getRefund(999L, 1L) }
            .isInstanceOf(RefundNotFoundException::class.java)
    }

    @Test
    fun 내_환불_목록을_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val refund = createRefund()
        val refundItem = createRefundItem()

        whenever(refundRepository.findByBuyerId(1L, pageable))
            .thenReturn(SliceImpl(listOf(refund), pageable, false))
        whenever(refundItemRepository.findByRefundIdIn(listOf(1L)))
            .thenReturn(listOf(refundItem))

        val result = service.getMyRefunds(1L, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].items).hasSize(1)
    }

    @Test
    fun 환불_목록이_비어있으면_빈_슬라이스를_반환한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(refundRepository.findByBuyerId(1L, pageable))
            .thenReturn(SliceImpl(emptyList(), pageable, false))

        val result = service.getMyRefunds(1L, pageable)

        assertThat(result.content).isEmpty()
    }

    @Test
    fun 판매자_환불_목록을_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val refund = createRefund()
        val refundItem = createRefundItem()

        whenever(refundRepository.findBySellerId(5L, pageable))
            .thenReturn(SliceImpl(listOf(refund), pageable, false))
        whenever(refundItemRepository.findByRefundIdIn(listOf(1L)))
            .thenReturn(listOf(refundItem))

        val result = service.getSellerRefunds(5L, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 판매자_환불_목록이_비어있으면_빈_슬라이스를_반환한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(refundRepository.findBySellerId(5L, pageable))
            .thenReturn(SliceImpl(emptyList(), pageable, false))

        val result = service.getSellerRefunds(5L, pageable)

        assertThat(result.content).isEmpty()
    }
}
