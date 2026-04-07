package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.domain.enums.MembershipGrade
import com.hoppingmall.user.dto.response.MembershipResponse
import com.hoppingmall.user.service.MembershipCommandService
import com.hoppingmall.user.service.MembershipQueryService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("MembershipController 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipControllerTest {

    @Mock
    private lateinit var membershipCommandService: MembershipCommandService

    @Mock
    private lateinit var membershipQueryService: MembershipQueryService

    @InjectMocks
    private lateinit var membershipController: MembershipController

    @Test
    fun createMembership은_201과_응답본문을_반환한다() {
        val principal = UserPrincipal.of(1L, Role.BUYER.name)
        val expected = membershipResponse(userId = 1L)
        whenever(membershipCommandService.createMembership(1L)).thenReturn(expected)

        val response = membershipController.createMembership(principal)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(ApiResponse.success(expected), response.body)
        verify(membershipCommandService).createMembership(1L)
    }

    @Test
    fun getMyMembership은_principal의_userId로_조회한다() {
        val principal = UserPrincipal.of(2L, Role.BUYER.name)
        val expected = membershipResponse(userId = 2L)
        whenever(membershipQueryService.getMembershipByUserId(2L)).thenReturn(expected)

        val response = membershipController.getMyMembership(principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ApiResponse.success(expected), response.body)
        verify(membershipQueryService).getMembershipByUserId(2L)
    }

    @Test
    fun getMembershipByUserId는_pathVariable로_조회한다() {
        val expected = membershipResponse(userId = 3L)
        whenever(membershipQueryService.getMembershipByUserId(3L)).thenReturn(expected)

        val response = membershipController.getMembershipByUserId(3L)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ApiResponse.success(expected), response.body)
        verify(membershipQueryService).getMembershipByUserId(3L)
    }

    private fun membershipResponse(userId: Long) = MembershipResponse(
        id = 1L,
        userId = userId,
        grade = MembershipGrade.BRONZE,
        gradeName = MembershipGrade.BRONZE.gradeName,
        totalSpent = BigDecimal.ZERO,
        pointEarningRate = MembershipGrade.BRONZE.pointEarningRate,
        discountRate = MembershipGrade.BRONZE.discountRate,
        nextGrade = MembershipGrade.SILVER,
        amountToNextGrade = MembershipGrade.SILVER.requiredAmount,
        createdAt = LocalDateTime.now(),
        updatedAt = null
    )
}
