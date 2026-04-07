package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.user.exception.seller.SellerBusinessNumberDuplicateException
import com.hoppingmall.user.exception.user.UserNotFoundException
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("SellerCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerCommandServiceImplTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var sellerRepository: SellerRepository

    @InjectMocks
    private lateinit var sellerCommandService: SellerCommandServiceImpl

    @Test
    fun 판매자_신청_성공_시_Seller를_저장한다() {
        val user = com.hoppingmall.user.domain.User.fixture().withId(1L)
        val sellerCaptor = argumentCaptor<Seller>()
        whenever(userRepository.findNullableById(1L)).thenReturn(user)
        whenever(sellerRepository.findNullableByUserId(1L)).thenReturn(null)
        whenever(sellerRepository.existsByBusinessNumber("123-45-67890")).thenReturn(false)
        whenever(sellerRepository.save(sellerCaptor.capture())).thenReturn(Seller.fixture())

        sellerCommandService.apply(1L, SellerApplyRequest("123-45-67890"))

        assertThat(sellerCaptor.firstValue.userId).isEqualTo(1L)
        assertThat(sellerCaptor.firstValue.businessNumber).isEqualTo("123-45-67890")
        assertThat(sellerCaptor.firstValue.getApprovalStatus()).isEqualTo(Seller.ApprovalStatus.PENDING)
        verify(sellerRepository).save(any())
    }

    @Test
    fun 이미_신청한_판매자면_예외가_발생한다() {
        whenever(userRepository.findNullableById(2L)).thenReturn(com.hoppingmall.user.domain.User.fixture().withId(2L))
        whenever(sellerRepository.findNullableByUserId(2L)).thenReturn(Seller.fixture())

        assertThatThrownBy { sellerCommandService.apply(2L, SellerApplyRequest("111-22-33333")) }
            .isInstanceOf(SellerAlreadyAppliedException::class.java)
    }

    @Test
    fun 사업자번호_중복이면_예외가_발생한다() {
        whenever(userRepository.findNullableById(3L)).thenReturn(com.hoppingmall.user.domain.User.fixture().withId(3L))
        whenever(sellerRepository.findNullableByUserId(3L)).thenReturn(null)
        whenever(sellerRepository.existsByBusinessNumber("999-88-77777")).thenReturn(true)

        assertThatThrownBy { sellerCommandService.apply(3L, SellerApplyRequest("999-88-77777")) }
            .isInstanceOf(SellerBusinessNumberDuplicateException::class.java)
    }

    @Test
    fun 사용자가_없으면_예외가_발생한다() {
        whenever(userRepository.findNullableById(404L)).thenReturn(null)

        assertThatThrownBy { sellerCommandService.apply(404L, SellerApplyRequest("000-00-00000")) }
            .isInstanceOf(UserNotFoundException::class.java)
    }
}
