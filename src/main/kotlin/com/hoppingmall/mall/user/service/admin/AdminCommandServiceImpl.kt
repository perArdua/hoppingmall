package com.hoppingmall.mall.user.service.admin

import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalStatusException
import com.hoppingmall.mall.user.exception.seller.SellerNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AdminCommandServiceImpl(
    private val sellerRepository: SellerRepository
) : AdminCommandService {

    override fun updateSellerApprovalStatus(sellerId: Long, request: SellerApprovalRequest) {
        val seller = sellerRepository.findById(sellerId)
            .orElseThrow { SellerNotFoundException() }

        when (request.toApprovalStatus()) {
            Seller.ApprovalStatus.APPROVED -> seller.approve()
            Seller.ApprovalStatus.REJECTED -> seller.reject()
            Seller.ApprovalStatus.PENDING -> throw SellerInvalidApprovalStatusException()
        }
    }
}
