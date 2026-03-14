package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.user.exception.seller.SellerBusinessNumberDuplicateException
import com.hoppingmall.user.exception.user.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
