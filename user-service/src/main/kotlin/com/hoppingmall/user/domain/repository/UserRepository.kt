package com.hoppingmall.user.domain.repository

import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun existsByEmail(email: Email): Boolean

    fun findNullableById(id: Long): User?

    fun findByEmail(email: Email): User?
}
