package com.hoppingmall.user.domain.repository

import com.hoppingmall.user.domain.Membership
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MembershipRepository : JpaRepository<Membership, Long> {
    fun findByUserId(userId: Long): Membership?
    fun existsByUserId(userId: Long): Boolean

    @Query("SELECT m FROM Membership m WHERE m.userId = :userId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserIdForUpdate(@Param("userId") userId: Long): Membership?
}
