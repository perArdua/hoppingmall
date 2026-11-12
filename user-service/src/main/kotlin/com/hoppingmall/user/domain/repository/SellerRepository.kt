package com.hoppingmall.user.domain.repository

import com.hoppingmall.user.domain.Seller
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.Optional

interface SellerRepository : JpaRepository<Seller, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    override fun findById(id: Long): Optional<Seller>

    fun findNullableByUserId(userId: Long): Seller?

    fun existsByBusinessNumber(businessNumber: String): Boolean
}
