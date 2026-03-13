package com.hoppingmall.mall.point.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType

@Repository
interface PointRepository : JpaRepository<Point, Long> {
    fun findByUserId(userId: Long): Point?
    
    @Query("SELECT p FROM Point p WHERE p.userId = :userId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserIdForUpdate(@Param("userId") userId: Long): Point?
} 