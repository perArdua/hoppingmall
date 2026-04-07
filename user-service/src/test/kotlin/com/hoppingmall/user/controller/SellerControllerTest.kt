package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.service.SellerCommandService
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

@ExtendWith(MockitoExtension::class)
@DisplayName("SellerController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerControllerTest {

    @Mock
    private lateinit var sellerCommandService: SellerCommandService

    @InjectMocks
    private lateinit var sellerController: SellerController

    @Test
    fun 판매자_신청을_principal의_userId로_위임한다() {
        val request = SellerApplyRequest("123-45-67890")
        val principal = UserPrincipal.of(10L, Role.SELLER.name)

        val response = sellerController.applyForSeller(request, principal)

        assertThat(response).isEqualTo(ApiResponse.success(Unit))
        verify(sellerCommandService).apply(10L, request)
    }
}
