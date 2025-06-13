package com.hoppingmall.mall.global.vo.password.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DefaultPasswordMaskingStrategyTest {

    @Test
    fun `비밀번호는 항상 마스킹된 값으로 리턴된다`() {
        // given
        val originalPassword = "MySecret123!"

        // when
        val masked = DefaultPasswordMaskingStrategy.mask()

        // then
        assertEquals("******", masked)
    }
}
