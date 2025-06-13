package com.hoppingmall.mall.user.controller.seller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.seller.SellerApplyRequest
import com.hoppingmall.mall.user.service.seller.SellerCommandService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails

class SellerControllerTest {

    private val sellerCommandService: SellerCommandService = mock()
    private val controller = SellerController(sellerCommandService)

    @Test
    fun `SELLER 사용자가 판매자 승인 신청을 하면 성공 응답이 반환된다`() {
        // given
        val request = SellerApplyRequest("123-45-67890")
        val userDetails: UserDetails = User.withUsername("10").password("pass").roles("SELLER").build()

        // when
        val response: ApiResponse<Unit> = controller.applyForSeller(request, userDetails)

        // then
        verify(sellerCommandService).apply(10L, request)
        assertEquals("SUCCESS", response.code)
        assertEquals("성공", response.message)
        assertEquals(Unit, response.data)
    }
}
