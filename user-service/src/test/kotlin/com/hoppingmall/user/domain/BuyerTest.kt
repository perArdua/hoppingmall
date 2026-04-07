package com.hoppingmall.user.domain

import com.hoppingmall.user.support.fixture.fixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("Buyer 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BuyerTest {

    @Test
    fun create는_User_ID를_정확히_연결한다() {
        val buyer = Buyer.fixture()

        assertEquals(1L, buyer.userId)
    }
}
