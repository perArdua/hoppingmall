package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.SettlementSellerLock
import com.hoppingmall.settlement.domain.repository.SettlementSellerLockRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Component
class SettlementSellerLocker(
    private val settlementSellerLockRepository: SettlementSellerLockRepository,
    transactionManager: PlatformTransactionManager
) {

    private val newTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun lock(sellerId: Long) {
        ensureLockRow(sellerId)
        settlementSellerLockRepository.findWithLockBySellerId(sellerId)
    }

    private fun ensureLockRow(sellerId: Long) {
        try {
            newTransaction.executeWithoutResult {
                if (!settlementSellerLockRepository.existsById(sellerId)) {
                    settlementSellerLockRepository.saveAndFlush(SettlementSellerLock(sellerId))
                }
            }
        } catch (e: DataIntegrityViolationException) {
            return
        }
    }
}
