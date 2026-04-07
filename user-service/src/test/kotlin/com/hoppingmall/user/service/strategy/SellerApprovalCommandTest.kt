package com.hoppingmall.user.service.strategy

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.exception.seller.SellerInvalidApprovalCommandException
import com.hoppingmall.user.support.fixture.fixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

@DisplayName("SellerApprovalCommand 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerApprovalCommandTest {

    private lateinit var mapper: SellerApprovalCommandMapper

    @BeforeEach
    fun setUp() {
        mapper = SellerApprovalCommandMapper(ApproveSellerCommand(), RejectSellerCommand())
    }

    @Test
    fun APPROVED_전략은_판매자_상태를_승인으로_변경한다() {
        val seller = Seller.fixture()

        mapper.getCommand(Seller.ApprovalStatus.APPROVED).execute(seller)

        assertEquals(Seller.ApprovalStatus.APPROVED, seller.getApprovalStatus())
    }

    @Test
    fun REJECTED_전략은_판매자_상태를_거절로_변경한다() {
        val seller = Seller.fixture()

        mapper.getCommand(Seller.ApprovalStatus.REJECTED).execute(seller)

        assertEquals(Seller.ApprovalStatus.REJECTED, seller.getApprovalStatus())
    }

    @Test
    fun PENDING은_유효한_커맨드가_아니라_예외가_발생한다() {
        assertThrows<SellerInvalidApprovalCommandException> {
            mapper.getCommand(Seller.ApprovalStatus.PENDING)
        }
    }
}
