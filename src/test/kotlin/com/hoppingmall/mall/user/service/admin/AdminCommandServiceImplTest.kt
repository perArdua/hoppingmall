package com.hoppingmall.mall.user.service.admin

import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalStatusException
import com.hoppingmall.mall.user.exception.seller.SellerNotFoundException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class AdminCommandServiceImplTest {

    private val sellerRepository: SellerRepository = mock()
    private val adminCommandService = AdminCommandServiceImpl(sellerRepository)

    @Test
    fun `판매자 승인 상태를 APPROVED로 변경할 수 있다`() {
        val seller = Seller.fixture(user = User.fixture())
        whenever(sellerRepository.findById(1L)).thenReturn(java.util.Optional.of(seller))

        val request = SellerApprovalRequest("APPROVED")

        adminCommandService.updateSellerApprovalStatus(1L, request)

        assert(seller.getApprovalStatus() == Seller.ApprovalStatus.APPROVED)
    }

    @Test
    fun `판매자 승인 상태를 REJECTED로 변경할 수 있다`() {
        val seller = Seller.fixture(user = User.fixture())
        whenever(sellerRepository.findById(1L)).thenReturn(java.util.Optional.of(seller))

        val request = SellerApprovalRequest("REJECTED")

        adminCommandService.updateSellerApprovalStatus(1L, request)

        assert(seller.getApprovalStatus() == Seller.ApprovalStatus.REJECTED)
    }

    @Test
    fun `존재하지 않는 판매자 ID일 경우 예외가 발생한다`() {
        whenever(sellerRepository.findById(any())).thenReturn(java.util.Optional.empty())

        val request = SellerApprovalRequest("APPROVED")

        assertThrows(SellerNotFoundException::class.java) {
            adminCommandService.updateSellerApprovalStatus(999L, request)
        }
    }

    @Test
    fun `승인 상태가 PENDING일 경우 예외가 발생한다`() {
        val seller = Seller.fixture(user = User.fixture())
        whenever(sellerRepository.findById(1L)).thenReturn(java.util.Optional.of(seller))

        val request = SellerApprovalRequest("PENDING")

        assertThrows(SellerInvalidApprovalStatusException::class.java) {
            adminCommandService.updateSellerApprovalStatus(1L, request)
        }
    }
}
