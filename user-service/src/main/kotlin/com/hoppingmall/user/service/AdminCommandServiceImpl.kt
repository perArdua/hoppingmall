package com.hoppingmall.user.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.exception.seller.SellerNotFoundException
import com.hoppingmall.user.service.strategy.SellerApprovalCommandMapper
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
        sellerRepository.findByIdOrNull(sellerId) ?: throw SellerNotFoundException() 
}
