package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.global.enums.Role
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SellerTest {

    @Test
    fun `Seller 생성 시 기본 승인 상태는 PENDING이다`() {
        val user = User.fixture(
            email = Email("seller@example.com"),
            password = Password("encoded"),
            name = "판매자",
            role = Role.SELLER
        )

        val seller = Seller.fixture(user = user)

        assertEquals(Seller.ApprovalStatus.PENDING, seller.getApprovalStatus())
        assertEquals("123-45-67890", seller.businessNumber)
        assertEquals(user, seller.user)
    }

    @Test
    fun `approve 호출 시 상태가 APPROVED로 변경된다`() {
        val seller = Seller.fixture(
            user = User.fixture(role = Role.SELLER)
        )

        seller.approve()

        assertEquals(Seller.ApprovalStatus.APPROVED, seller.getApprovalStatus())
    }

    @Test
    fun `reject 호출 시 상태가 REJECTED로 변경된다`() {
        val seller = Seller.fixture(
            user = User.fixture(role = Role.SELLER)
        )

        seller.reject()

        assertEquals(Seller.ApprovalStatus.REJECTED, seller.getApprovalStatus())
    }
}
