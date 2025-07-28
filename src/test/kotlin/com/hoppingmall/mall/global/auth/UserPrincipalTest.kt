package com.hoppingmall.mall.global.auth

import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("UserPrincipal")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserPrincipalTest {

    @Nested
    @DisplayName("생성")
    inner class Creation {
        @Test
        fun 역할을_지정하면_해당_역할이_권한에_포함된다() {
            val userId = 1L
            val role = "SELLER"

            val principal = UserPrincipal.of(userId, role)

            assertEquals(userId.toString(), principal.username)
            assertTrue(principal.authorities.any { it.authority == "ROLE_SELLER" })
        }

        @Test
        fun 역할을_생략하면_기본값_USER로_설정된다() {
            val userId = 1L

            val principal = UserPrincipal.of(userId)

            assertEquals("ROLE_USER", principal.authorities.first().authority)
            assertEquals(userId.toString(), principal.username)
        }
    }

    @Nested
    @DisplayName("getter 메서드")
    inner class GetterMethods {
        @Test
        fun 모든_getter_메서드는_예상한_값을_반환한다() {
            val userId = 100L
            val role = "ADMIN"
            val principal = UserPrincipal.of(userId, role)

            val authorities = principal.authorities
            val username = principal.username
            val password = principal.password
            val isExpired = principal.isAccountNonExpired
            val isLocked = principal.isAccountNonLocked
            val isCredExpired = principal.isCredentialsNonExpired
            val isEnabled = principal.isEnabled
            val id = principal.getUserId()

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
}
