package com.hoppingmall.mall.user.service.admin.strategy

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalStatusException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SellerApprovalCommand 전략 테스트")
class SellerApprovalCommandTest {

    @Test
    fun `APPROVED 전략은 seller 승인 상태로 변경한다`() {
        val seller = createPendingSeller()
        ApproveSellerCommand().execute(seller)
        assertEquals(Seller.ApprovalStatus.APPROVED, seller.getApprovalStatus())
    }

    @Test
    fun `REJECTED 전략은 seller 거절 상태로 변경한다`() {
        val seller = createPendingSeller()
        RejectSellerCommand().execute(seller)
        assertEquals(Seller.ApprovalStatus.REJECTED, seller.getApprovalStatus())
    }

    private fun createPendingSeller(): Seller {
        val user: User = User.fixture(
            email = Email("seller@example.com"),
            password = Password("encodedPassword123!"),
            name = "판매자테스트",
            role = Role.SELLER
        ).withId(1L)
        return Seller.fixture(user)
    }
}
