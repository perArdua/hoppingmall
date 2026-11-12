package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.exception.seller.SellerAlreadyProcessedException
import com.hoppingmall.user.exception.seller.SellerNotFoundException
import com.hoppingmall.user.exception.user.UserNotFoundException
import com.hoppingmall.user.service.strategy.SellerApprovalCommandMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AdminCommandServiceImpl(
    private val sellerRepository: SellerRepository,
    private val commandMapper: SellerApprovalCommandMapper,
    private val userRepository: UserRepository? = null
) : AdminCommandService {

    override fun updateSellerApprovalStatus(sellerId: Long, request: SellerApprovalRequest) {
        val seller = findSellerById(sellerId)

        val command = commandMapper.getCommand(request.toApprovalStatus())
        if (seller.getApprovalStatus() != Seller.ApprovalStatus.PENDING) {
            throw SellerAlreadyProcessedException()
        }
        command.execute(seller)

        if (seller.getApprovalStatus() == Seller.ApprovalStatus.APPROVED) {
            grantSellerRole(seller.userId)
        }
    }

    private fun grantSellerRole(userId: Long) {
        val repository = userRepository ?: return
        val user = repository.findNullableById(userId) ?: throw UserNotFoundException()
        user.grantSellerRole()
    }

    private fun findSellerById(sellerId: Long): Seller =
        sellerRepository.findById(sellerId).orElseThrow { SellerNotFoundException() }
}
