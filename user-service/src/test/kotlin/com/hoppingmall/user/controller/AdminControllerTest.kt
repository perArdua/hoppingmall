package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.service.AdminCommandService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("AdminController 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminControllerTest {

    @Mock
    private lateinit var adminCommandService: AdminCommandService

    @InjectMocks
    private lateinit var adminController: AdminController

    @Test
    fun updateSellerApprovalStatus는_service에_승인_변경을_위임한다() {
        val request = SellerApprovalRequest("APPROVED")

        val response = adminController.updateSellerApprovalStatus(1L, request)

        assertEquals(ApiResponse.success(Unit), response)
        verify(adminCommandService).updateSellerApprovalStatus(1L, request)
    }
}
