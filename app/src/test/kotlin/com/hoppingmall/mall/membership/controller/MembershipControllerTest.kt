package com.hoppingmall.mall.membership.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.membership.dto.response.MembershipResponse
import com.hoppingmall.mall.membership.enum.MembershipGrade
import com.hoppingmall.mall.membership.service.MembershipCommandService
import com.hoppingmall.mall.membership.service.MembershipQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("MembershipController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipControllerTest {

    private val membershipCommandService: MembershipCommandService = mock()
    private val membershipQueryService: MembershipQueryService = mock()
    private val controller = MembershipController(membershipCommandService, membershipQueryService)

    private val now = LocalDateTime.now()

    private fun createMembershipResponse(
        userId: Long = 1L,
        grade: MembershipGrade = MembershipGrade.BRONZE,
        totalSpent: BigDecimal = BigDecimal.ZERO
    ) = MembershipResponse(
        id = 1L,
        userId = userId,
        grade = grade,
        gradeName = grade.gradeName,
        totalSpent = totalSpent,
        pointEarningRate = grade.pointEarningRate,
        discountRate = grade.discountRate,
        nextGrade = grade.nextGrade(),
        amountToNextGrade = grade.nextGrade()?.requiredAmount?.subtract(totalSpent)?.coerceAtLeast(BigDecimal.ZERO),
        createdAt = now,
        updatedAt = null
    )

    @Nested
    @DisplayName("createMembership")
    inner class CreateMembership {
        @Test
        fun 멤버십_생성_성공() {
            val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
            val expectedResponse = createMembershipResponse()

            whenever(membershipCommandService.createMembership(userPrincipal.getUserId())).thenReturn(expectedResponse)

            val response: ResponseEntity<ApiResponse<MembershipResponse>> = controller.createMembership(userPrincipal)

            assertEquals(201, response.statusCode.value())
            assertEquals("SUCCESS", response.body?.code)
            assertEquals("성공", response.body?.message)
            assertEquals(expectedResponse, response.body?.data)
            verify(membershipCommandService).createMembership(userPrincipal.getUserId())
        }
    }

    @Nested
    @DisplayName("getMyMembership")
    inner class GetMyMembership {
        @Test
        fun 본인_멤버십_조회_성공() {
            val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
            val expectedResponse = createMembershipResponse(grade = MembershipGrade.SILVER, totalSpent = BigDecimal("150000"))

            whenever(membershipQueryService.getMembershipByUserId(userPrincipal.getUserId())).thenReturn(expectedResponse)

            val response: ResponseEntity<ApiResponse<MembershipResponse>> = controller.getMyMembership(userPrincipal)

            assertEquals(200, response.statusCode.value())
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(membershipQueryService).getMembershipByUserId(userPrincipal.getUserId())
        }
    }

    @Nested
    @DisplayName("getMembershipByUserId")
    inner class GetMembershipByUserId {
        @Test
        fun 관리자_멤버십_조회_성공() {
            val userId = 2L
            val expectedResponse = createMembershipResponse(userId = userId, grade = MembershipGrade.GOLD, totalSpent = BigDecimal("600000"))

            whenever(membershipQueryService.getMembershipByUserId(userId)).thenReturn(expectedResponse)

            val response: ResponseEntity<ApiResponse<MembershipResponse>> = controller.getMembershipByUserId(userId)

            assertEquals(200, response.statusCode.value())
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(membershipQueryService).getMembershipByUserId(userId)
        }
    }
}
