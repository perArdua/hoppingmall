package com.hoppingmall.product.internal.service

import com.hoppingmall.product.internal.domain.AppliedRequestKey
import com.hoppingmall.product.internal.domain.repository.AppliedRequestKeyRepository
import com.hoppingmall.product.internal.enums.AppliedRequestOperation
import com.hoppingmall.product.internal.exception.InvalidRequestKeyException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class AppliedRequestKeyGuard(
    private val appliedRequestKeyRepository: AppliedRequestKeyRepository,
    transactionManager: PlatformTransactionManager
) {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun applyOnce(operation: AppliedRequestOperation, requestKey: String, action: () -> Unit) {
        if (requestKey.isBlank() || requestKey.length > AppliedRequestKey.REQUEST_KEY_MAX_LENGTH) {
            throw InvalidRequestKeyException()
        }
        try {
            transactionTemplate.executeWithoutResult {
                if (isApplied(operation, requestKey)) return@executeWithoutResult
                appliedRequestKeyRepository.saveAndFlush(AppliedRequestKey(operation.name, requestKey))
                action()
            }
        } catch (e: DataIntegrityViolationException) {
            if (!isApplied(operation, requestKey)) throw e
        }
    }

    private fun isApplied(operation: AppliedRequestOperation, requestKey: String): Boolean =
        appliedRequestKeyRepository.existsByOperationAndRequestKey(operation.name, requestKey)

    companion object {
        const val REQUEST_KEY_HEADER = "X-Request-Key"
    }
}
