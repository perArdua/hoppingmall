package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.dto.response.SellerApplicationPageResponse
import com.hoppingmall.user.service.AdminCommandService
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

@ExtendWith(MockitoExtension::class)
@DisplayName("AdminController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminControllerTest {

    @Mock
    private lateinit var adminCommandService: AdminCommandService

    @Mock
    private lateinit var sellerQueryService: SellerQueryService

    @InjectMocks
    private lateinit var adminController: AdminController

    @Test
    fun 판매자_승인_상태_변경을_서비스에_위임한다() {
        val request = SellerApprovalRequest("APPROVED")

        val response = adminController.updateSellerApprovalStatus(1L, request)

        assertThat(response).isEqualTo(ApiResponse.success(Unit))
        verify(adminCommandService).updateSellerApprovalStatus(1L, request)
    }

    @Test
    fun 판매자_신청_목록을_상태와_페이지로_조회한다() {
        val expected = SellerApplicationPageResponse(emptyList(), 1, 10, 0L, 0)
        whenever(sellerQueryService.getApplications(Seller.ApprovalStatus.PENDING, 1, 10)).thenReturn(expected)

        val response = adminController.getSellerApplications(Seller.ApprovalStatus.PENDING, 1, 10)

        assertThat(response).isEqualTo(ApiResponse.success(expected))
    }
}
