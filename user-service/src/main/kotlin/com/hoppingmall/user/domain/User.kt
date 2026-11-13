package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "users")
class User private constructor(
    @Embedded
    val email: Email,

    @Embedded
    private var password: Password,

    @Column(nullable = false)
    private var name: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private var role: Role = Role.BUYER,

) : BaseEntity() {

    companion object {
        fun create(email: Email, password: Password, name: String, role: Role = Role.BUYER): User =
            User(email, password, name, role)
    }

    fun updatePassword(newPassword: Password) {
        this.password = newPassword
    }

    fun updateName(newName: String) {
        this.name = newName
    }

    fun grantSellerRole() {
        if (role == Role.BUYER) {
            role = Role.SELLER
        }
    }

    fun getPassword(): Password = password
    fun getName(): String = name
    fun getRole(): Role = role
}
