package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuyerTest {

    @Test
    fun `Buyer 생성 시 User 정보가 정확히 연결되어야 한다`() {
        val user = User.fixture(
            email = Email("buyer@example.com"),
            password = Password("encoded"),
            name = "구매자",
            role = Role.BUYER
        )

        val buyer = Buyer.fixture(user)

        assertEquals(user, buyer.user)
    }
}
