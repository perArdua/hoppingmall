package com.hoppingmall.product.internal.domain.repository

import com.hoppingmall.product.internal.domain.AppliedRequestKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppliedRequestKeyRepository : JpaRepository<AppliedRequestKey, Long> {
    fun existsByOperationAndRequestKey(operation: String, requestKey: String): Boolean
}
