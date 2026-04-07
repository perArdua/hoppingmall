package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Membership
import com.hoppingmall.user.domain.enums.MembershipGrade
import com.hoppingmall.user.domain.repository.MembershipRepository
import com.hoppingmall.user.exception.membership.MembershipAlreadyExistsException
import com.hoppingmall.user.support.withId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("MembershipCommandServiceImpl 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipCommandServiceImplTest {

    @Mock
    private lateinit var membershipRepository: MembershipRepository

    @InjectMocks
    private lateinit var membershipCommandService: MembershipCommandServiceImpl

    @Test
    fun createMembership는_멤버십이_없으면_생성한다() {
        whenever(membershipRepository.existsByUserId(1L)).thenReturn(false)
        whenever(membershipRepository.save(any<Membership>())).thenAnswer {
            (it.arguments[0] as Membership).withId(1L)
        }

        val response = membershipCommandService.createMembership(1L)

        assertEquals(1L, response.id)
        assertEquals(1L, response.userId)
        assertEquals(MembershipGrade.BRONZE, response.grade)
        assertEquals(BigDecimal.ZERO, response.totalSpent)
    }

    @Test
    fun createMembership는_이미_멤버십이_있으면_예외가_발생한다() {
        whenever(membershipRepository.existsByUserId(2L)).thenReturn(true)

        assertThrows<MembershipAlreadyExistsException> {
            membershipCommandService.createMembership(2L)
        }
    }

    @Test
    fun addPurchaseAmount는_기존_멤버십에_금액을_누적한다() {
        val membership = Membership.create(3L).withId(1L)
        whenever(membershipRepository.findByUserIdForUpdate(3L)).thenReturn(membership)
        whenever(membershipRepository.save(any<Membership>())).thenAnswer { it.arguments[0] }

        val response = membershipCommandService.addPurchaseAmount(3L, BigDecimal("100000"))

        assertEquals(BigDecimal("100000"), response.totalSpent)
        assertEquals(MembershipGrade.SILVER, response.grade)
        verify(membershipRepository).save(membership)
    }

    @Test
    fun addPurchaseAmount는_멤버십이_없으면_새로_생성한_후_금액을_누적한다() {
        whenever(membershipRepository.findByUserIdForUpdate(4L)).thenReturn(null)
        whenever(membershipRepository.save(any<Membership>())).thenAnswer {
            (it.arguments[0] as Membership).withId(2L)
        }

        val response = membershipCommandService.addPurchaseAmount(4L, BigDecimal("500000"))

        assertEquals(4L, response.userId)
        assertEquals(BigDecimal("500000"), response.totalSpent)
        assertEquals(MembershipGrade.GOLD, response.grade)
        verify(membershipRepository, times(2)).save(any<Membership>())
    }
}
