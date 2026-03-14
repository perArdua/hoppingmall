package com.hoppingmall.mall.user.service.admin

import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalCommandException
import com.hoppingmall.mall.user.exception.seller.SellerNotFoundException
import com.hoppingmall.mall.user.service.admin.strategy.SellerApprovalCommand
import com.hoppingmall.mall.user.service.admin.strategy.SellerApprovalCommandMapper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("AdminCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminCommandServiceImplTest {

    private val sellerRepository: SellerRepository = mock()
    private val commandMapper: SellerApprovalCommandMapper = mock()
    private val adminCommandService = AdminCommandServiceImpl(sellerRepository, commandMapper)

    @Nested
    @DisplayName("updateSellerApprovalStatus")
    inner class UpdateSellerApprovalStatus {
        @Test
        fun 판매자_승인_상태를_APPROVED로_변경할_수_있다() {
            val seller = Seller.fixture()
            val command = mock<SellerApprovalCommand>()

            whenever(sellerRepository.findById(1L)).thenReturn(java.util.Optional.of(seller))
            whenever(commandMapper.getCommand(Seller.ApprovalStatus.APPROVED)).thenReturn(command)

            val request = SellerApprovalRequest("APPROVED")

            adminCommandService.updateSellerApprovalStatus(1L, request)

            verify(command).execute(seller)
        }

        @Test
        fun 판매자_승인_상태를_REJECTED로_변경할_수_있다() {
            val seller = Seller.fixture()
            val command = mock<SellerApprovalCommand>()

            whenever(sellerRepository.findById(1L)).thenReturn(java.util.Optional.of(seller))
            whenever(commandMapper.getCommand(Seller.ApprovalStatus.REJECTED)).thenReturn(command)

            val request = SellerApprovalRequest("REJECTED")

            adminCommandService.updateSellerApprovalStatus(1L, request)

            verify(command).execute(seller)
        }

        @Test
        fun 존재하지_않는_판매자_ID일_경우_예외가_발생한다() {
            whenever(sellerRepository.findById(any())).thenReturn(java.util.Optional.empty())

            val request = SellerApprovalRequest("APPROVED")

            assertThrows(SellerNotFoundException::class.java) {
                adminCommandService.updateSellerApprovalStatus(999L, request)
            }
        }

        @Test
        fun 승인_상태가_PENDING일_경우_예외가_발생한다() {
            val seller = Seller.fixture()

            whenever(sellerRepository.findById(1L)).thenReturn(java.util.Optional.of(seller))
            whenever(commandMapper.getCommand(Seller.ApprovalStatus.PENDING)).thenThrow(
                SellerInvalidApprovalCommandException())

            val request = SellerApprovalRequest("PENDING")

            assertThrows(SellerInvalidApprovalCommandException::class.java) {
                adminCommandService.updateSellerApprovalStatus(1L, request)
            }
        }
    }
}
