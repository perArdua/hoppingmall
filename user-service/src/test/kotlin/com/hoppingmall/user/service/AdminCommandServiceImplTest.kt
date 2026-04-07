package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.exception.seller.SellerInvalidApprovalCommandException
import com.hoppingmall.user.exception.seller.SellerNotFoundException
import com.hoppingmall.user.service.strategy.ApproveSellerCommand
import com.hoppingmall.user.service.strategy.RejectSellerCommand
import com.hoppingmall.user.service.strategy.SellerApprovalCommandMapper
import com.hoppingmall.user.support.fixture.fixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@DisplayName("AdminCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminCommandServiceImplTest {

    @Mock
    private lateinit var sellerRepository: SellerRepository

    private lateinit var adminCommandService: AdminCommandServiceImpl

    @BeforeEach
    fun setUp() {
        adminCommandService = AdminCommandServiceImpl(
            sellerRepository = sellerRepository,
            commandMapper = SellerApprovalCommandMapper(ApproveSellerCommand(), RejectSellerCommand())
        )
    }

    @Test
    fun 판매자_승인_상태를_APPROVED로_변경할_수_있다() {
        val seller = Seller.fixture()
        whenever(sellerRepository.findById(1L)).thenReturn(Optional.of(seller))

        adminCommandService.updateSellerApprovalStatus(1L, SellerApprovalRequest("APPROVED"))

        assertThat(seller.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.APPROVED)
    }

    @Test
    fun 판매자_승인_상태를_REJECTED로_변경할_수_있다() {
        val seller = Seller.fixture()
        whenever(sellerRepository.findById(2L)).thenReturn(Optional.of(seller))

        adminCommandService.updateSellerApprovalStatus(2L, SellerApprovalRequest("REJECTED"))

        assertThat(seller.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.REJECTED)
    }

    @Test
    fun 존재하지_않는_판매자면_예외가_발생한다() {
        whenever(sellerRepository.findById(404L)).thenReturn(Optional.empty())

        assertThatThrownBy { adminCommandService.updateSellerApprovalStatus(404L, SellerApprovalRequest("APPROVED")) }
            .isInstanceOf(SellerNotFoundException::class.java)
    }

    @Test
    fun PENDING_승인_상태는_예외가_발생한다() {
        whenever(sellerRepository.findById(3L)).thenReturn(Optional.of(Seller.fixture()))

        assertThatThrownBy { adminCommandService.updateSellerApprovalStatus(3L, SellerApprovalRequest("PENDING")) }
            .isInstanceOf(SellerInvalidApprovalCommandException::class.java)
    }
}
