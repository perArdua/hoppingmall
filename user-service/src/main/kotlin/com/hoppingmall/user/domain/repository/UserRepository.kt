package com.hoppingmall.user.domain.repository

import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.domain.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun existsByEmail(email: Email): Boolean

    fun findNullableById(id: Long): User?

    fun findByEmail(email: Email): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findForUpdateById(@Param("id") id: Long): User?
}
