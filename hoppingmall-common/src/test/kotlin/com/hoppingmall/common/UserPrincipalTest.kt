package com.hoppingmall.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("UserPrincipal 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserPrincipalTest {

    @Test
    fun of_팩토리_메서드로_UserPrincipal을_생성한다() {
        val principal = UserPrincipal.of(1L, "BUYER")

        assertThat(principal.getUserId()).isEqualTo(1L)
        assertThat(principal.getRole()).isEqualTo("BUYER")
    }

    @Test
    fun getAuthorities는_ROLE_접두사가_붙은_권한을_반환한다() {
        val principal = UserPrincipal.of(1L, "SELLER")

        val authorities = principal.authorities

        assertThat(authorities).hasSize(1)
        assertThat(authorities.first().authority).isEqualTo("ROLE_SELLER")
    }

    @Test
    fun ADMIN_역할의_권한을_확인한다() {
        val principal = UserPrincipal.of(99L, "ADMIN")

        assertThat(principal.authorities.first().authority).isEqualTo("ROLE_ADMIN")
    }

    @Test
    fun getUsername은_userId를_문자열로_반환한다() {
        val principal = UserPrincipal.of(42L, "BUYER")

        assertThat(principal.username).isEqualTo("42")
    }

    @Test
    fun getPassword는_null을_반환한다() {
        val principal = UserPrincipal.of(1L, "BUYER")

        assertThat(principal.password).isNull()
    }

    @Test
    fun 계정_상태_플래그는_모두_true를_반환한다() {
        val principal = UserPrincipal.of(1L, "BUYER")

        assertThat(principal.isAccountNonExpired).isTrue()
        assertThat(principal.isAccountNonLocked).isTrue()
        assertThat(principal.isCredentialsNonExpired).isTrue()
        assertThat(principal.isEnabled).isTrue()
    }

    @Test
    fun getUserId는_생성자에_전달된_userId를_반환한다() {
        val principal = UserPrincipal.of(Long.MAX_VALUE, "BUYER")

        assertThat(principal.getUserId()).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun getRole은_생성자에_전달된_role을_반환한다() {
        val principal = UserPrincipal.of(1L, "CUSTOM_ROLE")

        assertThat(principal.getRole()).isEqualTo("CUSTOM_ROLE")
        assertThat(principal.authorities.first().authority).isEqualTo("ROLE_CUSTOM_ROLE")
    }
}
