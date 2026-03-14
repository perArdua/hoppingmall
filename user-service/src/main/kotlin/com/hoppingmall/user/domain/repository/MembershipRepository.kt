package com.hoppingmall.user.domain.repository

import com.hoppingmall.user.domain.Membership
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MembershipRepository : JpaRepository<Membership, Long> {
    fun findByUserId(userId: Long): Membership?
    fun existsByUserId(userId: Long): Boolean
}
