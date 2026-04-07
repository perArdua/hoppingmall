package com.hoppingmall.user.domain

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("User companion 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserCompanionTest {

    @Test
    fun create는_명시한_role로_사용자를_생성한다() {
        val user = User.create(
            email = Email("seller@example.com"),
            password = Password("encoded-password"),
            name = "판매자",
            role = Role.SELLER
        )

        assertEquals(Email("seller@example.com"), user.email)
        assertEquals("판매자", user.getName())
        assertEquals(Role.SELLER, user.getRole())
    }

    @Test
    fun create는_role을_생략하면_BUYER로_생성한다() {
        val user = User.create(
            email = Email("buyer@example.com"),
            password = Password("encoded-password"),
            name = "구매자"
        )

        assertEquals(Role.BUYER, user.getRole())
    }
}
