package com.hoppingmall.user.internal

import com.hoppingmall.user.domain.Membership
import com.hoppingmall.user.domain.enums.MembershipGrade
import com.hoppingmall.user.domain.repository.MembershipRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("InternalMembershipController 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InternalMembershipControllerTest {

    @Mock
    private lateinit var membershipRepository: MembershipRepository

    @InjectMocks
    private lateinit var controller: InternalMembershipController

    @Test
    fun 회원_적립률을_조회한다() {
        val userId = 1L
        val membership = Membership(userId = userId, grade = MembershipGrade.PLATINUM, totalSpent = BigDecimal("1000000"))
        whenever(membershipRepository.findByUserId(userId)).thenReturn(membership)

        val response = controller.getEarningRate(userId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(BigDecimal("0.05"), response.body)
    }

    @Test
    fun 멤버십이_없으면_BRONZE_적립률을_반환한다() {
        val userId = 999L
        whenever(membershipRepository.findByUserId(userId)).thenReturn(null)

        val response = controller.getEarningRate(userId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(BigDecimal("0.01"), response.body)
    }
}
