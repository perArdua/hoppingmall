package com.hoppingmall.product.internal.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "applied_request_keys",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_applied_request_keys_operation_key", columnNames = ["operation", "request_key"])
    ]
)
class AppliedRequestKey(
    @Column(name = "operation", nullable = false, length = 32)
    val operation: String,

    @Column(name = "request_key", nullable = false, length = REQUEST_KEY_MAX_LENGTH)
    val requestKey: String
) : BaseEntity() {

    companion object {
        const val REQUEST_KEY_MAX_LENGTH = 64
    }
}
