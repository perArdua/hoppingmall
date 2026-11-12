package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.user.exception.seller.SellerBusinessNumberDuplicateException
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("거절된 판매자 신청의 재신청")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerReapplyTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var sellerRepository: SellerRepository

    @InjectMocks
    private lateinit var sellerCommandService: SellerCommandServiceImpl

    @Test
    fun 거절된_신청자가_새_사업자번호로_다시_신청하면_같은_신청이_새_번호의_대기_상태로_돌아간다() {
        val rejected = rejectedSellerOf(USER_ID, "111-11-11111")
        givenUserAndApplication(rejected)
        whenever(sellerRepository.existsByBusinessNumber("222-22-22222")).thenReturn(false)

        sellerCommandService.apply(USER_ID, SellerApplyRequest("222-22-22222"))

        assertThat(rejected.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.PENDING)
        assertThat(rejected.businessNumber).isEqualTo("222-22-22222")
        verify(sellerRepository, never()).save(any())
    }

    @Test
    fun 거절된_신청자는_자기_사업자번호로_다시_신청할_수_있다() {
        val rejected = rejectedSellerOf(USER_ID, "111-11-11111")
        givenUserAndApplication(rejected)
        lenient().`when`(sellerRepository.existsByBusinessNumber("111-11-11111")).thenReturn(true)

        sellerCommandService.apply(USER_ID, SellerApplyRequest("111-11-11111"))

        assertThat(rejected.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.PENDING)
        assertThat(rejected.businessNumber).isEqualTo("111-11-11111")
        verify(sellerRepository, never()).save(any())
    }

    @Test
    fun 거절된_신청자가_다른_사람의_사업자번호로_다시_신청하면_사업자번호_중복으로_거절되고_신청은_그대로다() {
        val rejected = rejectedSellerOf(USER_ID, "111-11-11111")
        givenUserAndApplication(rejected)
        whenever(sellerRepository.existsByBusinessNumber("999-99-99999")).thenReturn(true)

        assertThatThrownBy { sellerCommandService.apply(USER_ID, SellerApplyRequest("999-99-99999")) }
            .isInstanceOf(SellerBusinessNumberDuplicateException::class.java)

        assertThat(rejected.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.REJECTED)
        assertThat(rejected.businessNumber).isEqualTo("111-11-11111")
    }

    @Test
    fun 승인된_신청자가_다시_신청하면_이미_신청함으로_거절된다() {
        val approved = Seller.fixture(userId = USER_ID, businessNumber = "111-11-11111").also { it.approve() }
        givenUserAndApplication(approved)

        assertThatThrownBy { sellerCommandService.apply(USER_ID, SellerApplyRequest("222-22-22222")) }
            .isInstanceOf(SellerAlreadyAppliedException::class.java)

        assertThat(approved.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.APPROVED)
    }

    @Test
    fun 대기_중인_신청자가_다시_신청하면_이미_신청함으로_거절된다() {
        val pending = Seller.fixture(userId = USER_ID, businessNumber = "111-11-11111")
        givenUserAndApplication(pending)

        assertThatThrownBy { sellerCommandService.apply(USER_ID, SellerApplyRequest("222-22-22222")) }
            .isInstanceOf(SellerAlreadyAppliedException::class.java)

        assertThat(pending.businessNumber).isEqualTo("111-11-11111")
    }

    private fun rejectedSellerOf(userId: Long, businessNumber: String): Seller =
        Seller.fixture(userId = userId, businessNumber = businessNumber).also { it.reject() }

    private fun givenUserAndApplication(seller: Seller) {
        whenever(userRepository.findNullableById(USER_ID)).thenReturn(User.fixture().withId(USER_ID))
        whenever(sellerRepository.findNullableByUserId(USER_ID)).thenReturn(seller)
    }

    companion object {
        private const val USER_ID = 7L
    }
}
