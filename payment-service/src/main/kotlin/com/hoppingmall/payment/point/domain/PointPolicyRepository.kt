package com.hoppingmall.payment.point.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointPolicyRepository : JpaRepository<PointPolicy, Long> {

    fun findByIsActiveTrue(): PointPolicy?

    fun existsByPolicyName(policyName: String): Boolean
}
