package com.hoppingmall.mall.membership.service

import com.hoppingmall.mall.membership.domain.Membership
import com.hoppingmall.mall.membership.domain.repository.MembershipRepository
import com.hoppingmall.mall.membership.dto.response.MembershipResponse
import com.hoppingmall.mall.membership.exception.MembershipAlreadyExistsException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class MembershipCommandServiceImpl(
    private val membershipRepository: MembershipRepository
) : MembershipCommandService {

    override fun createMembership(userId: Long): MembershipResponse {
        if (membershipRepository.existsByUserId(userId)) {
            throw MembershipAlreadyExistsException()
        }
        val membership = Membership.create(userId)
        val saved = membershipRepository.save(membership)
        return MembershipResponse.from(saved)
    }

    @CacheEvict(cacheNames = ["membership"], key = "#userId")
    override fun addPurchaseAmount(userId: Long, amount: BigDecimal): MembershipResponse {
        val membership = membershipRepository.findByUserId(userId)
            ?: membershipRepository.save(Membership.create(userId))
        membership.addPurchaseAmount(amount)
        val saved = membershipRepository.save(membership)
        return MembershipResponse.from(saved)
    }
}
