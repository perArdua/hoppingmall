package com.hoppingmall.mall.user.controller.admin

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.service.admin.AdminCommandService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("AdminController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminControllerTest {

    private val adminCommandService: AdminCommandService = mock()
    private val controller = AdminController(adminCommandService)

    @Nested
    @DisplayName("updateSellerApprovalStatus")
    inner class UpdateSellerApprovalStatus {
        @Test
        fun 판매자_승인_요청이_성공하면_응답_코드와_메시지는_SUCCESS를_반환하고_데이터는_Unit이다() {
            val sellerId = 1L
            val request = SellerApprovalRequest("APPROVED")

            val response: ApiResponse<Unit> = controller.updateSellerApprovalStatus(sellerId, request)

            verify(adminCommandService).updateSellerApprovalStatus(sellerId, request)
            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(Unit, response.data)
        }
    }
}
