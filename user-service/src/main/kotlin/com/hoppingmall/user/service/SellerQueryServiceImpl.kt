package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.response.AdminSellerApplicationResponse
import com.hoppingmall.user.dto.response.SellerApplicationPageResponse
import com.hoppingmall.user.dto.response.SellerApplicationResponse
import com.hoppingmall.user.exception.seller.SellerNotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SellerQueryServiceImpl(
    private val sellerRepository: SellerRepository,
    private val userRepository: UserRepository
) : SellerQueryService {

    override fun getMyApplication(userId: Long): SellerApplicationResponse {
        val application = sellerRepository.findNullableByUserId(userId) ?: throw SellerNotFoundException()
        return SellerApplicationResponse.from(application)
    }

    override fun getApplications(status: Seller.ApprovalStatus?, page: Int, size: Int): SellerApplicationPageResponse {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE), Sort.by(Sort.Direction.ASC, "id"))
        val applications = status
            ?.let { sellerRepository.findAllByApprovalStatus(it, pageable) }
            ?: sellerRepository.findAll(pageable)
        val applicants = userRepository.findAllById(applications.content.map { it.userId }.toSet())
            .associateBy { it.id }

        return SellerApplicationPageResponse(
            content = applications.content.map { AdminSellerApplicationResponse.of(it, applicants[it.userId]) },
            page = applications.number,
            size = applications.size,
            totalElements = applications.totalElements,
            totalPages = applications.totalPages
        )
    }

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
