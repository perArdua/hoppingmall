package com.hoppingmall.user.service

import com.hoppingmall.user.domain.repository.MembershipRepository
import com.hoppingmall.user.dto.response.MembershipResponse
import com.hoppingmall.user.exception.membership.MembershipNotFoundException
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MembershipQueryServiceImpl(
    private val membershipRepository: MembershipRepository
) : MembershipQueryService {

    @Cacheable(cacheNames = ["membership"], key = "#userId", sync = true)
    override fun getMembershipByUserId(userId: Long): MembershipResponse {
        val membership = membershipRepository.findByUserId(userId)
            ?: throw MembershipNotFoundException()
        return MembershipResponse.from(membership)
    }
}
