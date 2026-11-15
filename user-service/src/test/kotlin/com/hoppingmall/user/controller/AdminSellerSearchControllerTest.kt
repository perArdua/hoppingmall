package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.user.domain.Seller
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
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("AdminController 판매자 신청 검색")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminSellerSearchControllerTest {

    @Mock
    private lateinit var adminCommandService: AdminCommandService

    @Mock
    private lateinit var sellerQueryService: SellerQueryService

    @InjectMocks
    private lateinit var adminController: AdminController

    @Test
    fun 검색어를_상태_페이지와_함께_서비스에_넘긴다() {
        val expected = SellerApplicationPageResponse(emptyList(), 0, 20, 0L, 0)
        whenever(sellerQueryService.getApplications(Seller.ApprovalStatus.PENDING, 0, 20, "kim")).thenReturn(expected)

        val response = adminController.getSellerApplications(Seller.ApprovalStatus.PENDING, 0, 20, "kim")

        assertThat(response).isEqualTo(ApiResponse.success(expected))
    }
}
