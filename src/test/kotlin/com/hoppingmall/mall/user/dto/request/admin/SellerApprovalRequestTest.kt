package com.hoppingmall.mall.user.dto.request.admin

import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalStatusException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SellerApprovalRequestTest {

    @Test
    fun `존재하지 않는 승인 상태를 입력하면 SellerInvalidApprovalStatusException이 발생한다`() {
        val request = SellerApprovalRequest("INVALID")

        assertThrows(SellerInvalidApprovalStatusException::class.java) {
            request.toApprovalStatus()
        }
    }
}
