package com.hoppingmall.mall.user.service.seller

import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.seller.SellerApplyRequest
import com.hoppingmall.mall.user.exception.seller.SellerBusinessNumberDuplicateException
import com.hoppingmall.mall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class SellerCommandServiceImplTest {

    private val userRepository: UserRepository = mock()
    private val sellerRepository: SellerRepository = mock()
    private val sellerCommandService = SellerCommandServiceImpl(userRepository, sellerRepository)

    @Test
    fun `판매자 신청에 성공하면 Seller 엔티티가 저장된다`() {
        // given
        val userId = 1L
        val request = SellerApplyRequest("123-45-67890")
        val user = mock<User>()
        val captor = argumentCaptor<Seller>()

        whenever(userRepository.findNullableById(userId)).thenReturn(user)
        whenever(sellerRepository.findNullableByUserId(userId)).thenReturn(null)
        whenever(sellerRepository.existsByBusinessNumber(request.businessNumber)).thenReturn(false)
        whenever(sellerRepository.save(captor.capture())).thenReturn(mock())

        // when
        sellerCommandService.apply(userId, request)

        // then
        verify(sellerRepository).save(any())
        val savedSeller = captor.firstValue
        assert(savedSeller.user == user)
        assert(savedSeller.businessNumber == request.businessNumber)
    }

    @Test
    fun `이미 판매자 신청한 유저는 예외가 발생한다`() {
        // given
        val userId = 2L
        val request = SellerApplyRequest("111-22-33333")

        whenever(userRepository.findNullableById(userId)).thenReturn(mock())
        whenever(sellerRepository.findNullableByUserId(userId)).thenReturn(mock())

        // when & then
        assertThrows(SellerAlreadyAppliedException::class.java) {
            sellerCommandService.apply(userId, request)
        }
    }

    @Test
    fun `사업자번호가 이미 등록되어 있다면 예외 발생`() {
        // given
        val userId = 3L
        val request = SellerApplyRequest("999-88-77777")

        whenever(userRepository.findNullableById(userId)).thenReturn(mock())
        whenever(sellerRepository.findNullableByUserId(userId)).thenReturn(null)
        whenever(sellerRepository.existsByBusinessNumber(request.businessNumber)).thenReturn(true)

        // when & then
        assertThrows(SellerBusinessNumberDuplicateException::class.java) {
            sellerCommandService.apply(userId, request)
        }
    }

    @Test
    fun `사용자가 존재하지 않으면 예외 발생`() {
        // given
        val userId = 999L
        val request = SellerApplyRequest("000-00-00000")

        whenever(userRepository.findNullableById(userId)).thenReturn(null)

        // when & then
        assertThrows(UserNotFoundException::class.java) {
            sellerCommandService.apply(userId, request)
        }
    }
}
