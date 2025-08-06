package com.hoppingmall.mall.point.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryRepository : JpaRepository<PointHistory, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory>
    
    fun findByUserId(userId: Long, pageable: Pageable): Page<PointHistory>
} 