package com.hoppingmall.mall.user.dto.request.admin

import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalStatusException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertThrows

@DisplayName("SellerApprovalRequest")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerApprovalRequestTest {

    @Nested
    @DisplayName("toApprovalStatus")
    inner class ToApprovalStatus {
        @Test
        fun 존재하지_않는_승인_상태를_입력하면_SellerInvalidApprovalStatusException이_발생한다() {
            val request = SellerApprovalRequest("INVALID")

            assertThrows(SellerInvalidApprovalStatusException::class.java) {
                request.toApprovalStatus()
            }
        }
    }
}
