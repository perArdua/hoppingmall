package com.hoppingmall.settlement.domain.repository

import com.hoppingmall.settlement.domain.SettlementSellerLock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface SettlementSellerLockRepository : JpaRepository<SettlementSellerLock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockBySellerId(sellerId: Long): SettlementSellerLock?
}
