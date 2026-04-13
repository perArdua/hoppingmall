package com.hoppingmall.user.domain

import com.hoppingmall.user.exception.seller.SellerInvalidApprovalStatusException
import com.hoppingmall.user.support.fixture.fixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("Seller")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerTest {

    @Test
    fun 기본_승인_상태가_PENDING이다() {
        val seller = Seller.fixture()

        assertThat(seller.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.PENDING)
        assertThat(seller.businessNumber).isEqualTo("123-45-67890")
        assertThat(seller.userId).isEqualTo(1L)
    }

    @Test
    fun 승인_시_상태를_APPROVED로_변경한다() {
        val seller = Seller.fixture()

        seller.approve()

        assertThat(seller.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.APPROVED)
    }

    @Test
    fun 거절_시_상태를_REJECTED로_변경한다() {
        val seller = Seller.fixture()

        seller.reject()

        assertThat(seller.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.REJECTED)
    }

    @Test
    fun 이미_승인된_판매자를_다시_승인하면_예외가_발생한다() {
        val seller = Seller.fixture()
        seller.approve()

        assertThatThrownBy { seller.approve() }
            .isInstanceOf(SellerInvalidApprovalStatusException::class.java)
    }

    @Test
    fun 이미_승인된_판매자를_거절하면_예외가_발생한다() {
        val seller = Seller.fixture()
        seller.approve()

        assertThatThrownBy { seller.reject() }
            .isInstanceOf(SellerInvalidApprovalStatusException::class.java)
    }

    @Test
    fun 이미_거절된_판매자를_승인하면_예외가_발생한다() {
        val seller = Seller.fixture()
        seller.reject()

        assertThatThrownBy { seller.approve() }
            .isInstanceOf(SellerInvalidApprovalStatusException::class.java)
    }

    @Test
    fun 이미_거절된_판매자를_거절하면_예외가_발생한다() {
        val seller = Seller.fixture()
        seller.reject()

        assertThatThrownBy { seller.reject() }
            .isInstanceOf(SellerInvalidApprovalStatusException::class.java)
    }
}
