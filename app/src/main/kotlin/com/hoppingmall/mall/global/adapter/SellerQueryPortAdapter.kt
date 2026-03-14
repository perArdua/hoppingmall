package com.hoppingmall.mall.global.adapter

import com.hoppingmall.mall.user.api.SellerInfo
import com.hoppingmall.mall.user.api.SellerQueryPort
import com.hoppingmall.mall.user.domain.repository.SellerRepository
import org.springframework.stereotype.Component

@Component
class SellerQueryPortAdapter(
    private val sellerRepository: SellerRepository
) : SellerQueryPort {

    override fun findByUserId(userId: Long): SellerInfo? {
        return sellerRepository.findNullableByUserId(userId)?.let {
            SellerInfo(id = it.id!!, userId = it.userId)
        }
    }
}
