package com.hoppingmall.mall.user.service.admin.strategy

import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalCommandException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

@DisplayName("SellerApprovalCommand")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerApprovalCommandTest {

    private lateinit var mapper: SellerApprovalCommandMapper
    private lateinit var approveCommand: ApproveSellerCommand
    private lateinit var rejectCommand: RejectSellerCommand

    @BeforeEach
    fun setUp() {
        approveCommand = ApproveSellerCommand()
        rejectCommand = RejectSellerCommand()
        mapper = SellerApprovalCommandMapper(approveCommand, rejectCommand)
    }

    @Nested
    @DisplayName("ApproveSellerCommand")
    inner class ApproveSellerCommandTest {
        @Test
        fun APPROVED_전략은_seller_승인_상태로_변경한다() {
            val seller = Seller.fixture()
            val command = mapper.getCommand(Seller.ApprovalStatus.APPROVED)
            
            command.execute(seller)
            
            assertEquals(Seller.ApprovalStatus.APPROVED, seller.getApprovalStatus())
        }
    }

    @Nested
    @DisplayName("RejectSellerCommand")
    inner class RejectSellerCommandTest {
        @Test
        fun REJECTED_전략은_seller_거절_상태로_변경한다() {
            val seller = Seller.fixture()
            val command = mapper.getCommand(Seller.ApprovalStatus.REJECTED)
            
            command.execute(seller)
            
            assertEquals(Seller.ApprovalStatus.REJECTED, seller.getApprovalStatus())
        }
    }

    @Nested
    @DisplayName("SellerApprovalCommandMapper")
    inner class SellerApprovalCommandMapperTest {
        @Test
        fun PENDING_상태로_Command를_요청하면_예외가_발생한다() {
            assertThrows(SellerInvalidApprovalCommandException::class.java) {
                mapper.getCommand(Seller.ApprovalStatus.PENDING)
            }
        }
    }
}
