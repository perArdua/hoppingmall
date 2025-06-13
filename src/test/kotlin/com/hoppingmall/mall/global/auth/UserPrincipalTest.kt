package com.hoppingmall.mall.global.auth

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserPrincipalTest {

    @Test
    fun `역할을 지정하면 해당 역할이 권한에 포함된다`() {
        // given
        val userId = 1L
        val role = "SELLER"

        // when
        val principal = UserPrincipal.of(userId, role)

        // then
        assertEquals(userId.toString(), principal.username)
        assertTrue(principal.authorities.any { it.authority == "ROLE_SELLER" })
    }

    @Test
    fun `역할을 생략하면 기본값 USER로 설정된다`() {
        // given
        val userId = 1L

        // when
        val principal = UserPrincipal.of(userId)

        // then
        assertEquals("ROLE_USER", principal.authorities.first().authority)
        assertEquals(userId.toString(), principal.username)
    }

    @Test
    fun `모든 getter 메서드는 예상한 값을 반환한다`() {
        // given
        val userId = 100L
        val role = "ADMIN"
        val principal = UserPrincipal.of(userId, role)

        // when
        val authorities = principal.authorities
        val username = principal.username
        val password = principal.password
        val isExpired = principal.isAccountNonExpired
        val isLocked = principal.isAccountNonLocked
        val isCredExpired = principal.isCredentialsNonExpired
        val isEnabled = principal.isEnabled
        val id = principal.getUserId()

        // then
        assertEquals("ROLE_ADMIN", authorities.first().authority)
        assertEquals("100", username)
        assertNull(password)
        assertTrue(isExpired)
        assertTrue(isLocked)
        assertTrue(isCredExpired)
        assertTrue(isEnabled)
        assertEquals(100L, id)
    }
}
