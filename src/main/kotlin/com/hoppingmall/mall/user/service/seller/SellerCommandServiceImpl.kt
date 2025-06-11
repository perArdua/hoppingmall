package com.hoppingmall.mall.user.service.seller

import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.SellerApplyRequest
import com.hoppingmall.mall.user.exception.seller.SellerBusinessNumberDuplicateException
import com.hoppingmall.mall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class SellerCommandServiceImpl(
    private val userRepository: UserRepository,
    private val sellerRepository: SellerRepository
) : SellerCommandService {

    @Transactional
    override fun apply(userId: Long, request: SellerApplyRequest) {
        val user = userRepository.findNullableById(userId)
            ?: throw UserNotFoundException()

        if (sellerRepository.findNullableByUserId(userId) != null) {
            throw SellerAlreadyAppliedException()
        }

        if (sellerRepository.existsByBusinessNumber(request.businessNumber)) {
            throw SellerBusinessNumberDuplicateException()
        }

        val seller = Seller.create(user, request.businessNumber)
        sellerRepository.save(seller)
    }
}
