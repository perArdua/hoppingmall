package com.hoppingmall.user.domain

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.support.fixture.fixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("User 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserTest {

    @Test
    fun fixture는_User_필드를_정확히_매핑한다() {
        val user = User.fixture(
            email = Email("test@example.com"),
            password = Password("encoded-password"),
            name = "홍길동",
            role = Role.SELLER
        )

        assertEquals(Email("test@example.com"), user.email)
        assertEquals("홍길동", user.getName())
        assertEquals(Role.SELLER, user.getRole())
    }

    @Test
    fun updateName은_이름을_변경한다() {
        val user = User.fixture(name = "기존이름")

        user.updateName("변경된이름")

        assertEquals("변경된이름", user.getName())
    }

    @Test
    fun updatePassword는_비밀번호를_변경한다() {
        val user = User.fixture(password = Password("old-password"))

        user.updatePassword(Password("new-password"))

        assertEquals("new-password", user.getPassword().value)
    }
}
