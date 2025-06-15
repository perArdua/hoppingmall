package com.hoppingmall.mall.user.controller.admin

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.service.admin.AdminCommandService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AdminControllerTest {

    private val adminCommandService: AdminCommandService = mock()
    private val controller = AdminController(adminCommandService)

    @Test
    fun `판매자 승인 요청이 성공하면 응답 코드와 메시지는 SUCCESS를 반환하고 데이터는 Unit이다`() {
        // given
        val sellerId = 1L
        val request = SellerApprovalRequest("APPROVED")

        // when
        val response: ApiResponse<Unit> = controller.updateSellerApprovalStatus(sellerId, request)

        // then
        verify(adminCommandService).updateSellerApprovalStatus(sellerId, request)
        assertEquals("SUCCESS", response.code)
        assertEquals("성공", response.message)
        assertEquals(Unit, response.data)
    }
}
