package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.global.enums.Role
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals

@DisplayName("Seller")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerTest {

    @Nested
    @DisplayName("생성")
    inner class Creation {
        @Test
        fun Seller_생성_시_기본_승인_상태는_PENDING이다() {
            val seller = Seller.fixture()

            assertEquals(Seller.ApprovalStatus.PENDING, seller.getApprovalStatus())
            assertEquals("123-45-67890", seller.businessNumber)
            assertEquals(1L, seller.userId)
        }
    }

    @Nested
    @DisplayName("approve")
    inner class Approve {
        @Test
        fun approve_호출_시_상태가_APPROVED로_변경된다() {
            val seller = Seller.fixture()

            seller.approve()

            assertEquals(Seller.ApprovalStatus.APPROVED, seller.getApprovalStatus())
        }
    }

    @Nested
    @DisplayName("reject")
    inner class Reject {
        @Test
        fun reject_호출_시_상태가_REJECTED로_변경된다() {
            val seller = Seller.fixture()

            seller.reject()

            assertEquals(Seller.ApprovalStatus.REJECTED, seller.getApprovalStatus())
        }
    }
}
