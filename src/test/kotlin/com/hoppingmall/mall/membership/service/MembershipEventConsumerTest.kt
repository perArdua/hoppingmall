package com.hoppingmall.mall.membership.service

import com.hoppingmall.mall.membership.domain.MembershipEventLog
import com.hoppingmall.mall.membership.domain.repository.MembershipEventLogRepository
import com.hoppingmall.mall.membership.dto.response.MembershipResponse
import com.hoppingmall.mall.membership.enum.MembershipGrade
import com.hoppingmall.mall.payment.dto.event.MembershipUpdateRequestEvent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("MembershipEventConsumer 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipEventConsumerTest {

    @Mock
    private lateinit var membershipCommandService: MembershipCommandService

    @Mock
    private lateinit var membershipEventLogRepository: MembershipEventLogRepository

    @InjectMocks
    private lateinit var membershipEventConsumer: MembershipEventConsumer

    private fun createEvent(
        eventId: String = "membership-TXN_123456",
        userId: Long = 1L,
        orderId: Long = 1L,
        paymentId: Long = 1L,
        amount: BigDecimal = BigDecimal("50000")
    ) = MembershipUpdateRequestEvent(eventId, userId, orderId, paymentId, amount)

    private fun membershipResponse() = MembershipResponse(
        id = 1L,
        userId = 1L,
        grade = MembershipGrade.BRONZE,
        gradeName = MembershipGrade.BRONZE.gradeName,
        totalSpent = BigDecimal("50000"),
        pointEarningRate = MembershipGrade.BRONZE.pointEarningRate,
        discountRate = MembershipGrade.BRONZE.discountRate,
        nextGrade = MembershipGrade.SILVER,
        amountToNextGrade = BigDecimal("50000"),
        createdAt = LocalDateTime.now(),
        updatedAt = null
    )

    @Test
    fun 멤버십_구매금액_업데이트_정상_처리() {
        val event = createEvent()

        whenever(membershipEventLogRepository.existsByEventId(event.eventId)).thenReturn(false)
        whenever(membershipCommandService.addPurchaseAmount(event.userId, event.amount))
            .thenReturn(membershipResponse())
        whenever(membershipEventLogRepository.save(any<MembershipEventLog>()))
            .thenAnswer { it.arguments[0] }

        membershipEventConsumer.handleMembershipUpdateRequest(event)

        verify(membershipCommandService).addPurchaseAmount(event.userId, event.amount)
        verify(membershipEventLogRepository).save(any<MembershipEventLog>())
    }

    @Test
    fun 중복_이벤트는_처리를_건너뛴다() {
        val event = createEvent()

        whenever(membershipEventLogRepository.existsByEventId(event.eventId)).thenReturn(true)

        membershipEventConsumer.handleMembershipUpdateRequest(event)

        verify(membershipCommandService, never()).addPurchaseAmount(any(), any())
        verify(membershipEventLogRepository, never()).save(any<MembershipEventLog>())
    }

    @Test
    fun DataIntegrityViolationException_발생_시_정상_종료() {
        val event = createEvent()

        whenever(membershipEventLogRepository.existsByEventId(event.eventId)).thenReturn(false)
        whenever(membershipCommandService.addPurchaseAmount(event.userId, event.amount))
            .thenReturn(membershipResponse())
        whenever(membershipEventLogRepository.save(any<MembershipEventLog>()))
            .thenThrow(DataIntegrityViolationException("duplicate"))

        membershipEventConsumer.handleMembershipUpdateRequest(event)

        verify(membershipCommandService).addPurchaseAmount(event.userId, event.amount)
    }
}
