package com.hoppingmall.user.domain

import com.hoppingmall.user.support.fixture.fixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("Buyer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BuyerTest {

    @Test
    fun 생성_시_User_ID를_정확히_연결한다() {
        val buyer = Buyer.fixture()

        assertThat(buyer.userId).isEqualTo(1L)
    }
}
