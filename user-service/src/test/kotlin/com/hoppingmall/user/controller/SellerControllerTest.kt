package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.dto.response.SellerApplicationResponse
import com.hoppingmall.user.service.SellerCommandService
import com.hoppingmall.user.service.SellerQueryService
import org.assertj.core.api.Assertions.assertThat
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
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("SellerController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerControllerTest {

    @Mock
    private lateinit var sellerCommandService: SellerCommandService

    @Mock
    private lateinit var sellerQueryService: SellerQueryService

    @InjectMocks
    private lateinit var sellerController: SellerController

    @Test
    fun 판매자_신청을_principal의_userId로_위임하고_신청_id와_상태를_돌려준다() {
        val request = SellerApplyRequest("123-45-67890")
        val principal = UserPrincipal.of(10L, Role.SELLER.name)
        val expected = application(Seller.ApprovalStatus.PENDING)
        whenever(sellerCommandService.apply(10L, request)).thenReturn(expected)

        val response = sellerController.applyForSeller(request, principal)

        assertThat(response).isEqualTo(ApiResponse.success(expected))
        verify(sellerCommandService).apply(10L, request)
    }

    @Test
    fun 내_신청은_principal의_userId로_조회한다() {
        val expected = application(Seller.ApprovalStatus.REJECTED, "서류 미비")
        whenever(sellerQueryService.getMyApplication(10L)).thenReturn(expected)

        val response = sellerController.getMyApplication(UserPrincipal.of(10L, Role.BUYER.name))

        assertThat(response).isEqualTo(ApiResponse.success(expected))
    }

    private fun application(status: Seller.ApprovalStatus, reason: String? = null) = SellerApplicationResponse(
        id = 3L,
        userId = 10L,
        businessNumber = "123-45-67890",
        status = status,
        rejectionReason = reason,
        appliedAt = LocalDateTime.of(2026, 11, 15, 9, 0),
        updatedAt = null
    )
}
