package com.hoppingmall.mall.membership.service

import com.hoppingmall.mall.membership.domain.Membership
import com.hoppingmall.mall.membership.domain.repository.MembershipRepository
import com.hoppingmall.mall.membership.enum.MembershipGrade
import com.hoppingmall.mall.membership.exception.MembershipAlreadyExistsException
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal

@DisplayName("MembershipCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipCommandServiceImplTest {

    private val membershipRepository: MembershipRepository = mock()
    private val membershipCommandService = MembershipCommandServiceImpl(membershipRepository)

    @Nested
    @DisplayName("createMembership")
    inner class CreateMembership {
        @Test
        fun 멤버십_생성_성공() {
            val userId = 1L
            val membership = Membership.Companion.fixture(userId = userId)

            whenever(membershipRepository.existsByUserId(userId)).thenReturn(false)
            whenever(membershipRepository.save(any<Membership>())).thenReturn(membership)

            val response = membershipCommandService.createMembership(userId)

            assertEquals(userId, response.userId)
            assertEquals(MembershipGrade.BRONZE, response.grade)
            verify(membershipRepository).existsByUserId(userId)
            verify(membershipRepository).save(any())
        }

        @Test
        fun 이미_멤버십이_존재하면_예외_발생() {
            val userId = 1L

            whenever(membershipRepository.existsByUserId(userId)).thenReturn(true)

            assertThrows(MembershipAlreadyExistsException::class.java) {
                membershipCommandService.createMembership(userId)
            }

            verify(membershipRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("addPurchaseAmount")
    inner class AddPurchaseAmount {
        @Test
        fun 기존_멤버십에_구매_금액_추가_성공() {
            val userId = 1L
            val amount = BigDecimal("50000")
            val membership = Membership.Companion.fixture(userId = userId)

            whenever(membershipRepository.findByUserId(userId)).thenReturn(membership)
            whenever(membershipRepository.save(any<Membership>())).thenReturn(membership)

            val response = membershipCommandService.addPurchaseAmount(userId, amount)

            assertEquals(BigDecimal("50000"), response.totalSpent)
            verify(membershipRepository).findByUserId(userId)
            verify(membershipRepository).save(any())
        }

        @Test
        fun 멤버십이_없으면_자동_생성_후_금액_추가() {
            val userId = 1L
            val amount = BigDecimal("50000")
            val newMembership = Membership.Companion.fixture(userId = userId)

            whenever(membershipRepository.findByUserId(userId)).thenReturn(null)
            whenever(membershipRepository.save(any<Membership>())).thenReturn(newMembership)

            val response = membershipCommandService.addPurchaseAmount(userId, amount)

            assertEquals(userId, response.userId)
            verify(membershipRepository).findByUserId(userId)
            verify(membershipRepository, times(2)).save(any())
        }

        @Test
        fun 구매_금액_추가_시_등급_승급() {
            val userId = 1L
            val amount = BigDecimal("100000")
            val membership = Membership.Companion.fixture(userId = userId)

            whenever(membershipRepository.findByUserId(userId)).thenReturn(membership)
            whenever(membershipRepository.save(any<Membership>())).thenAnswer { it.arguments[0] }

            val response = membershipCommandService.addPurchaseAmount(userId, amount)

            assertEquals(MembershipGrade.SILVER, response.grade)
            assertEquals(BigDecimal("100000"), response.totalSpent)
        }
    }
}
