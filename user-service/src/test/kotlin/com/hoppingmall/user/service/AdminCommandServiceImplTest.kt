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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("AdminCommandServiceImpl 단위 테스트")
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

        assertEquals(Seller.ApprovalStatus.APPROVED, seller.getApprovalStatus())
    }

    @Test
    fun 판매자_승인_상태를_REJECTED로_변경할_수_있다() {
        val seller = Seller.fixture()
        whenever(sellerRepository.findById(2L)).thenReturn(Optional.of(seller))

        adminCommandService.updateSellerApprovalStatus(2L, SellerApprovalRequest("REJECTED"))

        assertEquals(Seller.ApprovalStatus.REJECTED, seller.getApprovalStatus())
    }

    @Test
    fun 존재하지_않는_판매자면_예외가_발생한다() {
        whenever(sellerRepository.findById(404L)).thenReturn(Optional.empty())

        assertThrows<SellerNotFoundException> {
            adminCommandService.updateSellerApprovalStatus(404L, SellerApprovalRequest("APPROVED"))
        }
    }

    @Test
    fun PENDING_승인_상태는_예외가_발생한다() {
        whenever(sellerRepository.findById(3L)).thenReturn(Optional.of(Seller.fixture()))

        assertThrows<SellerInvalidApprovalCommandException> {
            adminCommandService.updateSellerApprovalStatus(3L, SellerApprovalRequest("PENDING"))
        }
    }
}
