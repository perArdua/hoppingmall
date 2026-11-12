package com.hoppingmall.user.domain

import com.hoppingmall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.user.support.fixture.fixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("Seller 재신청")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerReapplyDomainTest {

    @Test
    fun 거절된_신청을_다시_신청하면_새_사업자번호의_PENDING이_된다() {
        val seller = Seller.fixture(businessNumber = "111-11-11111").also { it.reject() }

        seller.reapply("222-22-22222")

        assertThat(seller.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.PENDING)
        assertThat(seller.businessNumber).isEqualTo("222-22-22222")
    }

    @Test
    fun 대기_중인_신청은_다시_신청할_수_없다() {
        val seller = Seller.fixture(businessNumber = "111-11-11111")

        assertThatThrownBy { seller.reapply("222-22-22222") }
            .isInstanceOf(SellerAlreadyAppliedException::class.java)
        assertThat(seller.businessNumber).isEqualTo("111-11-11111")
    }

    @Test
    fun 승인된_신청은_다시_신청할_수_없다() {
        val seller = Seller.fixture(businessNumber = "111-11-11111").also { it.approve() }

        assertThatThrownBy { seller.reapply("222-22-22222") }
            .isInstanceOf(SellerAlreadyAppliedException::class.java)
        assertThat(seller.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.APPROVED)
    }
}
