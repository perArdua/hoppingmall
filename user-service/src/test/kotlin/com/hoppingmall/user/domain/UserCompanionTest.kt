package com.hoppingmall.user.domain

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("User companion")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserCompanionTest {

    @Test
    fun 명시한_role로_사용자를_생성한다() {
        val user = User.create(
            email = Email("seller@example.com"),
            password = Password("encoded-password"),
            name = "판매자",
            role = Role.SELLER
        )

        assertThat(user.email).isEqualTo(Email("seller@example.com"))
        assertThat(user.getName()).isEqualTo("판매자")
        assertThat(user.getRole()).isEqualTo(Role.SELLER)
    }

    @Test
    fun role_생략_시_BUYER로_생성한다() {
        val user = User.create(
            email = Email("buyer@example.com"),
            password = Password("encoded-password"),
            name = "구매자"
        )

        assertThat(user.getRole()).isEqualTo(Role.BUYER)
    }
}
