package com.hoppingmall.mall.global.auth

import com.hoppingmall.mall.global.enums.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.*

class UserPrincipal(
    private val userId: Long,
    private val email: String,
    private val role: String
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_$role"))
    }

    override fun getPassword(): String? = null

    override fun getUsername(): String = userId.toString()

    override fun isAccountNonExpired(): Boolean = true
    
    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true

    fun getUserId(): Long = userId

    fun getEmail(): String = email

    fun getRole(): String = role

    companion object {
        fun of(userId: Long, role: String): UserPrincipal {
            return UserPrincipal(userId, "", role)
        }
    }
}
