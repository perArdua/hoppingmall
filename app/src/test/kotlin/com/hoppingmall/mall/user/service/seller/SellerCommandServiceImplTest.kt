package com.hoppingmall.mall.user.service.seller

import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.seller.SellerApplyRequest
import com.hoppingmall.mall.user.exception.seller.SellerBusinessNumberDuplicateException
import com.hoppingmall.mall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.kotlin.*

@DisplayName("SellerCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerCommandServiceImplTest {

    private val userRepository: UserRepository = mock()
    private val sellerRepository: SellerRepository = mock()
    private val sellerCommandService = SellerCommandServiceImpl(userRepository, sellerRepository)

    @Nested
    @DisplayName("apply")
    inner class Apply {
        @Test
        fun 판매자_신청에_성공하면_Seller_엔티티가_저장된다() {
            val userId = 1L
            val request = SellerApplyRequest("123-45-67890")
            val user = mock<User>()
            val captor = argumentCaptor<Seller>()

            whenever(userRepository.findNullableById(userId)).thenReturn(user)
            whenever(sellerRepository.findNullableByUserId(userId)).thenReturn(null)
            whenever(sellerRepository.existsByBusinessNumber(request.businessNumber)).thenReturn(false)
            whenever(sellerRepository.save(captor.capture())).thenReturn(mock())

            sellerCommandService.apply(userId, request)

            verify(sellerRepository).save(any())
            val savedSeller = captor.firstValue
            assert(savedSeller.userId == user.id)
            assert(savedSeller.businessNumber == request.businessNumber)
        }

        @Test
        fun 이미_판매자_신청한_유저는_예외가_발생한다() {
            val userId = 2L
            val request = SellerApplyRequest("111-22-33333")

            whenever(userRepository.findNullableById(userId)).thenReturn(mock())
            whenever(sellerRepository.findNullableByUserId(userId)).thenReturn(mock())

            assertThrows(SellerAlreadyAppliedException::class.java) {
                sellerCommandService.apply(userId, request)
            }
        }

        @Test
        fun 사업자번호가_이미_등록되어_있다면_예외_발생() {
            val userId = 3L
            val request = SellerApplyRequest("999-88-77777")

            whenever(userRepository.findNullableById(userId)).thenReturn(mock())
            whenever(sellerRepository.findNullableByUserId(userId)).thenReturn(null)
            whenever(sellerRepository.existsByBusinessNumber(request.businessNumber)).thenReturn(true)

            assertThrows(SellerBusinessNumberDuplicateException::class.java) {
                sellerCommandService.apply(userId, request)
            }
        }

        @Test
        fun 사용자가_존재하지_않으면_예외_발생() {
            val userId = 999L
            val request = SellerApplyRequest("000-00-00000")

            whenever(userRepository.findNullableById(userId)).thenReturn(null)

            assertThrows(UserNotFoundException::class.java) {
                sellerCommandService.apply(userId, request)
            }
        }
    }
}
