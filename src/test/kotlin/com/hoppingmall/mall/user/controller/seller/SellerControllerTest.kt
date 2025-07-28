package com.hoppingmall.mall.user.controller.seller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.dto.request.seller.SellerApplyRequest
import com.hoppingmall.mall.user.service.seller.SellerCommandService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails

@DisplayName("SellerController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerControllerTest {

    private val sellerCommandService: SellerCommandService = mock()
    private val controller = SellerController(sellerCommandService)

    @Nested
    @DisplayName("applyForSeller")
    inner class ApplyForSeller {
        @Test
        fun SELLER_사용자가_판매자_승인_신청을_하면_성공_응답이_반환된다() {
            val request = SellerApplyRequest("123-45-67890")
            val userDetails: UserDetails = User.withUsername("10").password("pass").roles("SELLER").build()

            val response: ApiResponse<Unit> = controller.applyForSeller(request, userDetails)

            verify(sellerCommandService).apply(10L, request)
            assertEquals("SUCCESS", response.code)
            assertEquals("성공", response.message)
            assertEquals(Unit, response.data)
        }
    }
}
