package com.hoppingmall.payment.point.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PointPolicyRepository : JpaRepository<PointPolicy, Long> {

    fun findByIsActiveTrue(): PointPolicy?

    fun existsByPolicyName(policyName: String): Boolean

    fun findAllByOrderByIdDesc(pageable: Pageable): Slice<PointPolicy>

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PointPolicy p SET p.isActive = false, p.updatedAt = CURRENT_TIMESTAMP WHERE p.isActive = true")
    fun deactivateAll(): Int

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PointPolicy p SET p.isActive = false, p.updatedAt = CURRENT_TIMESTAMP WHERE p.isActive = true AND p.id <> :keepId")
    fun deactivateAllExcept(@Param("keepId") keepId: Long): Int
}
