package com.hoppingmall.mall.point.service.strategy

import com.hoppingmall.mall.membership.domain.Membership
import com.hoppingmall.mall.membership.domain.repository.MembershipRepository
import com.hoppingmall.mall.membership.enum.MembershipGrade
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@DisplayName("MembershipBasedRateStrategy")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class MembershipBasedRateStrategyTest {

    @Mock
    private lateinit var membershipRepository: MembershipRepository

    @InjectMocks
    private lateinit var strategy: MembershipBasedRateStrategy

    @Test
    fun BRONZE_등급은_1퍼센트_적립률을_반환한다() {
        val membership = Membership.fixture(grade = MembershipGrade.BRONZE)
        whenever(membershipRepository.findByUserId(1L)).thenReturn(membership)

        val earnRate = strategy.getEarnRate(1L)

        assertEquals(MembershipGrade.BRONZE.pointEarningRate, earnRate)
    }

    @Test
    fun SILVER_등급은_2퍼센트_적립률을_반환한다() {
        val membership = Membership.fixture(grade = MembershipGrade.SILVER)
        whenever(membershipRepository.findByUserId(1L)).thenReturn(membership)

        val earnRate = strategy.getEarnRate(1L)

        assertEquals(MembershipGrade.SILVER.pointEarningRate, earnRate)
    }

    @Test
    fun GOLD_등급은_3퍼센트_적립률을_반환한다() {
        val membership = Membership.fixture(grade = MembershipGrade.GOLD)
        whenever(membershipRepository.findByUserId(1L)).thenReturn(membership)

        val earnRate = strategy.getEarnRate(1L)

        assertEquals(MembershipGrade.GOLD.pointEarningRate, earnRate)
    }

    @Test
    fun PLATINUM_등급은_5퍼센트_적립률을_반환한다() {
        val membership = Membership.fixture(grade = MembershipGrade.PLATINUM)
        whenever(membershipRepository.findByUserId(1L)).thenReturn(membership)

        val earnRate = strategy.getEarnRate(1L)

        assertEquals(MembershipGrade.PLATINUM.pointEarningRate, earnRate)
    }

    @Test
    fun DIAMOND_등급은_7퍼센트_적립률을_반환한다() {
        val membership = Membership.fixture(grade = MembershipGrade.DIAMOND)
        whenever(membershipRepository.findByUserId(1L)).thenReturn(membership)

        val earnRate = strategy.getEarnRate(1L)

        assertEquals(MembershipGrade.DIAMOND.pointEarningRate, earnRate)
    }

    @Test
    fun Membership이_없으면_BRONZE_적립률을_반환한다() {
        whenever(membershipRepository.findByUserId(999L)).thenReturn(null)

        val earnRate = strategy.getEarnRate(999L)

        assertEquals(MembershipGrade.BRONZE.pointEarningRate, earnRate)
    }
}
