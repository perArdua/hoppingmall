package com.hoppingmall.mall.user.service.admin

import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalStatusException
import com.hoppingmall.mall.user.exception.seller.SellerNotFoundException
import com.hoppingmall.mall.user.service.admin.strategy.SellerApprovalCommandMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AdminCommandServiceImpl(
    private val sellerRepository: SellerRepository,
    private val commandMapper: SellerApprovalCommandMapper
) : AdminCommandService {

    override fun updateSellerApprovalStatus(sellerId: Long, request: SellerApprovalRequest) {
        val seller = findSellerById(sellerId)

        val command = commandMapper.getCommand(request.toApprovalStatus())
        command.execute(seller)
    }

    private fun findSellerById(sellerId: Long): Seller =
        sellerRepository.findById(sellerId).orElseThrow { SellerNotFoundException() }
}
