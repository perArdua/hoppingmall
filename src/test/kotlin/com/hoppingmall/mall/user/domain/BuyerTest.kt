package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals

@DisplayName("Buyer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BuyerTest {

    @Nested
    @DisplayName("생성")
    inner class Creation {
        @Test
        fun Buyer_생성_시_User_정보가_정확히_연결되어야_한다() {
            val buyer = Buyer.fixture()

            assertEquals(Role.BUYER, buyer.user.getRole())
            assertEquals("구매자", buyer.user.getName())
        }
    }
}
