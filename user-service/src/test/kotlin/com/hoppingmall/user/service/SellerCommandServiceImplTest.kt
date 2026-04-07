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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("SellerCommandServiceImpl 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerCommandServiceImplTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var sellerRepository: SellerRepository

    @InjectMocks
    private lateinit var sellerCommandService: SellerCommandServiceImpl

    @Test
    fun apply는_판매자_신청에_성공하면_Seller를_저장한다() {
        val user = com.hoppingmall.user.domain.User.fixture().withId(1L)
        val sellerCaptor = argumentCaptor<Seller>()
        whenever(userRepository.findNullableById(1L)).thenReturn(user)
        whenever(sellerRepository.findNullableByUserId(1L)).thenReturn(null)
        whenever(sellerRepository.existsByBusinessNumber("123-45-67890")).thenReturn(false)
        whenever(sellerRepository.save(sellerCaptor.capture())).thenReturn(Seller.fixture())

        sellerCommandService.apply(1L, SellerApplyRequest("123-45-67890"))

        assertEquals(1L, sellerCaptor.firstValue.userId)
        assertEquals("123-45-67890", sellerCaptor.firstValue.businessNumber)
        assertEquals(Seller.ApprovalStatus.PENDING, sellerCaptor.firstValue.getApprovalStatus())
        verify(sellerRepository).save(any())
    }

    @Test
    fun apply는_이미_신청한_판매자면_예외가_발생한다() {
        whenever(userRepository.findNullableById(2L)).thenReturn(com.hoppingmall.user.domain.User.fixture().withId(2L))
        whenever(sellerRepository.findNullableByUserId(2L)).thenReturn(Seller.fixture())

        assertThrows<SellerAlreadyAppliedException> {
            sellerCommandService.apply(2L, SellerApplyRequest("111-22-33333"))
        }
    }

    @Test
    fun apply는_사업자번호가_중복이면_예외가_발생한다() {
        whenever(userRepository.findNullableById(3L)).thenReturn(com.hoppingmall.user.domain.User.fixture().withId(3L))
        whenever(sellerRepository.findNullableByUserId(3L)).thenReturn(null)
        whenever(sellerRepository.existsByBusinessNumber("999-88-77777")).thenReturn(true)

        assertThrows<SellerBusinessNumberDuplicateException> {
            sellerCommandService.apply(3L, SellerApplyRequest("999-88-77777"))
        }
    }

    @Test
    fun apply는_사용자가_없으면_예외가_발생한다() {
        whenever(userRepository.findNullableById(404L)).thenReturn(null)

        assertThrows<UserNotFoundException> {
            sellerCommandService.apply(404L, SellerApplyRequest("000-00-00000"))
        }
    }
}
