package com.hoppingmall.user.grpc

import com.hoppingmall.user.domain.Membership
import com.hoppingmall.user.domain.enums.MembershipGrade
import com.hoppingmall.user.domain.repository.MembershipRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("GrpcMembershipQueryService 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GrpcMembershipQueryServiceTest {

    @Mock
    private lateinit var membershipRepository: MembershipRepository

    @InjectMocks
    private lateinit var grpcMembershipQueryService: GrpcMembershipQueryService

    @Test
    fun 회원_등급별_적립률을_반환한다() = runBlocking {
        val userId = 1L
        val membership = Membership(userId = userId, grade = MembershipGrade.GOLD, totalSpent = BigDecimal("500000"))
        whenever(membershipRepository.findByUserId(userId)).thenReturn(membership)

        val request = userIdRequest { this.userId = userId }
        val response = grpcMembershipQueryService.getPointEarningRate(request)

        assertEquals("0.03", response.earningRate)
    }

    @Test
    fun 멤버십이_없으면_BRONZE_적립률을_반환한다() = runBlocking {
        val userId = 999L
        whenever(membershipRepository.findByUserId(userId)).thenReturn(null)

        val request = userIdRequest { this.userId = userId }
        val response = grpcMembershipQueryService.getPointEarningRate(request)

        assertEquals("0.01", response.earningRate)
    }

    @Test
    fun DIAMOND_등급_적립률을_반환한다() = runBlocking {
        val userId = 2L
        val membership = Membership(userId = userId, grade = MembershipGrade.DIAMOND, totalSpent = BigDecimal("5000000"))
        whenever(membershipRepository.findByUserId(userId)).thenReturn(membership)

        val request = userIdRequest { this.userId = userId }
        val response = grpcMembershipQueryService.getPointEarningRate(request)

        assertEquals("0.07", response.earningRate)
    }
}
