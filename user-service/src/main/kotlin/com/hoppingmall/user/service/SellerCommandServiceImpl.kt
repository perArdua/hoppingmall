package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.user.exception.seller.SellerBusinessNumberDuplicateException
import com.hoppingmall.user.exception.user.UserNotFoundException
import org.springframework.dao.DataIntegrityViolationException
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

        val application = sellerRepository.findNullableByUserId(userId)
        if (application != null && application.getApprovalStatus() != Seller.ApprovalStatus.REJECTED) {
            throw SellerAlreadyAppliedException()
        }

        if (application?.businessNumber != request.businessNumber &&
            sellerRepository.existsByBusinessNumber(request.businessNumber)
        ) {
            throw SellerBusinessNumberDuplicateException()
        }

        if (application != null) {
            application.reapply(request.businessNumber)
            return
        }

        val seller = Seller.create(user, request.businessNumber)
        saveApplication(seller)
    }

    private fun saveApplication(seller: Seller) {
        try {
            sellerRepository.save(seller)
        } catch (ex: DataIntegrityViolationException) {
            if (ex.mostSpecificCause.message?.contains(Seller.USER_ID_UNIQUE_CONSTRAINT, ignoreCase = true) == true) {
                throw SellerAlreadyAppliedException()
            }
            throw ex
        }
    }
}
