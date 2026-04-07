package com.hoppingmall.user.domain

import com.hoppingmall.user.support.fixture.fixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("Seller 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerTest {

    @Test
    fun fixture는_기본_승인_상태가_PENDING이다() {
        val seller = Seller.fixture()

        assertEquals(Seller.ApprovalStatus.PENDING, seller.getApprovalStatus())
        assertEquals("123-45-67890", seller.businessNumber)
        assertEquals(1L, seller.userId)
    }

    @Test
    fun approve는_상태를_APPROVED로_변경한다() {
        val seller = Seller.fixture()

        seller.approve()

        assertEquals(Seller.ApprovalStatus.APPROVED, seller.getApprovalStatus())
    }

    @Test
    fun reject는_상태를_REJECTED로_변경한다() {
        val seller = Seller.fixture()

        seller.reject()

        assertEquals(Seller.ApprovalStatus.REJECTED, seller.getApprovalStatus())
    }
}
