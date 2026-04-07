package com.hoppingmall.user.domain

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.support.fixture.fixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("User")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserTest {

    @Test
    fun 필드를_정확히_매핑한다() {
        val user = User.fixture(
            email = Email("test@example.com"),
            password = Password("encoded-password"),
            name = "홍길동",
            role = Role.SELLER
        )

        assertThat(user.email).isEqualTo(Email("test@example.com"))
        assertThat(user.getName()).isEqualTo("홍길동")
        assertThat(user.getRole()).isEqualTo(Role.SELLER)
    }

    @Test
    fun 이름_변경_성공() {
        val user = User.fixture(name = "기존이름")

        user.updateName("변경된이름")

        assertThat(user.getName()).isEqualTo("변경된이름")
    }

    @Test
    fun 비밀번호_변경_성공() {
        val user = User.fixture(password = Password("old-password"))

        user.updatePassword(Password("new-password"))

        assertThat(user.getPassword().value).isEqualTo("new-password")
    }
}
