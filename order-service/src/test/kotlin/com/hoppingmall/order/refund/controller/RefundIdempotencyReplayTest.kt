package com.hoppingmall.order.refund.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.IdempotencyAspect
import com.hoppingmall.idempotency.IdempotencyRecord
import com.hoppingmall.idempotency.IdempotencyService
import com.hoppingmall.order.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.refund.service.RefundCommandService
import com.hoppingmall.order.refund.service.RefundQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@DisplayName("환불 요청·승인·거절의 멱등성 키 응답 재생")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class RefundIdempotencyReplayTest {

    private val refundCommandService: RefundCommandService = mock()
    private val refundQueryService: RefundQueryService = mock()
    private val idempotencyService: IdempotencyService = mock()
    private val redissonClient: RedissonClient = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val records = ConcurrentHashMap<String, IdempotencyRecord>()

    private val controller: RefundController = AspectJProxyFactory(RefundController(refundCommandService, refundQueryService))
        .apply {
            isProxyTargetClass = true
            addAspect(IdempotencyAspect(idempotencyService, redissonClient, objectMapper))
        }
        .getProxy()

    private val buyer = UserPrincipal.of(BUYER_ID, "BUYER")
    private val seller = UserPrincipal.of(SELLER_ID, "SELLER")
    private val createRequest = RefundCreateRequest(
        orderId = 10L, reason = RefundReason.CHANGE_OF_MIND, items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
    )
    private val rejectRequest = RefundApprovalRequest(rejectionReason = "상품 훼손")

    @BeforeEach
    fun setUp() {
        val lock: RLock = mock()
        whenever(redissonClient.getLock(any<String>())).thenReturn(lock)
        whenever(lock.tryLock(any(), any(), any())).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(idempotencyService.findByKey(any())).thenAnswer { records[it.getArgument<String>(0)] }
        whenever(idempotencyService.save(any(), any(), any(), any(), any(), any())).thenAnswer {
            val record = IdempotencyRecord(
                idempotencyKey = it.getArgument(0),
                httpMethod = it.getArgument(1),
                endpoint = it.getArgument(2),
                responseStatus = it.getArgument(3),
                responseBody = it.getArgument(4),
                expiresAt = LocalDateTime.now().plusHours(it.getArgument<Long>(5))
            )
            records[record.idempotencyKey] = record
            record
        }
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun 같은_키로_환불을_두_번_요청하면_두_번째는_첫_응답을_그대로_받고_환불은_한_번만_요청된다() {
        whenever(refundCommandService.requestRefund(BUYER_ID, createRequest)).thenReturn(refund(RefundStatus.REQUESTED))

        val first = call("POST", "/api/v1/refunds", BUYER_ID, "refund-key") { controller.requestRefund(createRequest, buyer) }
        val second = call("POST", "/api/v1/refunds", BUYER_ID, "refund-key") { controller.requestRefund(createRequest, buyer) }

        verify(refundCommandService, times(1)).requestRefund(BUYER_ID, createRequest)
        assertSameReplay(asResponse(first), asResponse(second))
    }

    @Test
    fun 같은_키로_환불을_두_번_승인하면_두_번째는_첫_응답을_그대로_받고_승인은_한_번만_실행된다() {
        whenever(refundCommandService.approveRefund(REFUND_ID, SELLER_ID)).thenReturn(refund(RefundStatus.COMPLETED))

        val first = call("PATCH", APPROVE_URI, SELLER_ID, "approve-key") { controller.approveRefund(REFUND_ID, seller) }
        val second = call("PATCH", APPROVE_URI, SELLER_ID, "approve-key") { controller.approveRefund(REFUND_ID, seller) }

        verify(refundCommandService, times(1)).approveRefund(REFUND_ID, SELLER_ID)
        assertSameReplay(asResponse(first), asResponse(second))
    }

    @Test
    fun 같은_키로_환불을_두_번_거절하면_두_번째는_첫_응답을_그대로_받고_거절은_한_번만_실행된다() {
        whenever(refundCommandService.rejectRefund(REFUND_ID, SELLER_ID, rejectRequest)).thenReturn(refund(RefundStatus.REJECTED))

        val first = call("PATCH", REJECT_URI, SELLER_ID, "reject-key") { controller.rejectRefund(REFUND_ID, rejectRequest, seller) }
        val second = call("PATCH", REJECT_URI, SELLER_ID, "reject-key") { controller.rejectRefund(REFUND_ID, rejectRequest, seller) }

        verify(refundCommandService, times(1)).rejectRefund(REFUND_ID, SELLER_ID, rejectRequest)
        assertSameReplay(asResponse(first), asResponse(second))
    }

    @Test
    fun 다른_키로_보낸_환불_승인은_다시_실행된다() {
        whenever(refundCommandService.approveRefund(REFUND_ID, SELLER_ID)).thenReturn(refund(RefundStatus.COMPLETED))

        call("PATCH", APPROVE_URI, SELLER_ID, "approve-key-1") { controller.approveRefund(REFUND_ID, seller) }
        call("PATCH", APPROVE_URI, SELLER_ID, "approve-key-2") { controller.approveRefund(REFUND_ID, seller) }

        verify(refundCommandService, times(2)).approveRefund(REFUND_ID, SELLER_ID)
    }

    private fun call(method: String, uri: String, userId: Long, key: String, action: () -> Any?): Any? {
        val request = MockHttpServletRequest(method, uri)
        request.addHeader("X-User-Id", userId.toString())
        request.addHeader("Idempotency-Key", key)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return action()
    }

    private fun asResponse(result: Any?): ResponseEntity<*> {
        assertThat(result).isInstanceOf(ResponseEntity::class.java)
        return result as ResponseEntity<*>
    }

    private fun assertSameReplay(first: ResponseEntity<*>, second: ResponseEntity<*>) {
        assertThat(first.statusCode.value()).isEqualTo(200)
        assertThat(second.statusCode).isEqualTo(first.statusCode)
        assertThat(objectMapper.writeValueAsString(second.body)).isEqualTo(objectMapper.writeValueAsString(first.body))
    }

    private fun refund(status: RefundStatus): RefundResponse {
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        return RefundResponse(
            id = REFUND_ID, orderId = 10L, paymentId = 20L, buyerId = BUYER_ID, sellerId = SELLER_ID,
            status = status, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            refundAmount = BigDecimal("10000.50"), isFullRefund = false,
            rejectionReason = if (status == RefundStatus.REJECTED) "상품 훼손" else null,
            approvedBy = if (status == RefundStatus.REQUESTED) null else SELLER_ID,
            completedAt = if (status == RefundStatus.COMPLETED) now else null,
            createdAt = now, updatedAt = now, items = emptyList()
        )
    }

    companion object {
        private const val REFUND_ID = 1L
        private const val BUYER_ID = 1L
        private const val SELLER_ID = 5L
        private const val APPROVE_URI = "/api/v1/refunds/1/approve"
        private const val REJECT_URI = "/api/v1/refunds/1/reject"
    }
}
