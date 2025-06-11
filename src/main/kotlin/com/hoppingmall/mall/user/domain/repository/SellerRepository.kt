package com.hoppingmall.mall.user.domain.repository

import com.hoppingmall.mall.user.domain.Seller
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SellerRepository : JpaRepository<Seller, Long> {

    fun findNullableByUserId(userId: Long): Seller?

    fun existsByBusinessNumber(businessNumber: String): Boolean
}
