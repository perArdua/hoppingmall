package com.hoppingmall.mall.membership.service

import com.hoppingmall.mall.membership.domain.Membership
import com.hoppingmall.mall.membership.domain.repository.MembershipRepository
import com.hoppingmall.mall.membership.enum.MembershipGrade
import com.hoppingmall.mall.membership.exception.MembershipNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal

@DisplayName("MembershipQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipQueryServiceImplTest {

    private val membershipRepository: MembershipRepository = mock()
    private val membershipQueryService = MembershipQueryServiceImpl(membershipRepository)

    @Nested
    @DisplayName("getMembershipByUserId")
    inner class GetMembershipByUserId {
        @Test
        fun 멤버십_조회_성공() {
            val userId = 1L
            val membership = Membership.Companion.fixture(userId = userId, grade = MembershipGrade.SILVER, totalSpent = BigDecimal("150000"))

            whenever(membershipRepository.findByUserId(userId)).thenReturn(membership)

            val response = membershipQueryService.getMembershipByUserId(userId)

            assertEquals(userId, response.userId)
            assertEquals(MembershipGrade.SILVER, response.grade)
            assertEquals("실버", response.gradeName)
            assertEquals(BigDecimal("150000"), response.totalSpent)
            assertEquals(BigDecimal("0.02"), response.pointEarningRate)
            assertEquals(BigDecimal("0.01"), response.discountRate)
            verify(membershipRepository).findByUserId(userId)
        }

        @Test
        fun 멤버십이_없으면_예외_발생() {
            val userId = 1L

            whenever(membershipRepository.findByUserId(userId)).thenReturn(null)

            assertThrows(MembershipNotFoundException::class.java) {
                membershipQueryService.getMembershipByUserId(userId)
            }

            verify(membershipRepository).findByUserId(userId)
        }

        @Test
        fun 다음_등급까지_남은_금액_계산() {
            val userId = 1L
            val membership = Membership.Companion.fixture(userId = userId, grade = MembershipGrade.SILVER, totalSpent = BigDecimal("150000"))

            whenever(membershipRepository.findByUserId(userId)).thenReturn(membership)

            val response = membershipQueryService.getMembershipByUserId(userId)

            assertEquals(MembershipGrade.GOLD, response.nextGrade)
            assertEquals(BigDecimal("350000"), response.amountToNextGrade)
        }

        @Test
        fun DIAMOND_등급은_다음_등급_없음() {
            val userId = 1L
            val membership = Membership.Companion.fixture(userId = userId, grade = MembershipGrade.DIAMOND, totalSpent = BigDecimal("5000000"))

            whenever(membershipRepository.findByUserId(userId)).thenReturn(membership)

            val response = membershipQueryService.getMembershipByUserId(userId)

            assertNull(response.nextGrade)
            assertNull(response.amountToNextGrade)
        }
    }
}
