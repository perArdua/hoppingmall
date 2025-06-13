package com.hoppingmall.mall.global.common.config

import com.hoppingmall.mall.global.vo.password.policy.DefaultPasswordPolicy
import com.hoppingmall.mall.global.vo.password.policy.PasswordPolicy
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasswordPolicyConfigTest {

    private val config = PasswordPolicyConfig()

    @Test
    fun `passwordPolicy 빈은 DefaultPasswordPolicy 인스턴스를 반환한다`() {
        // when
        val policy: PasswordPolicy = config.passwordPolicy()

        // then
        assertNotNull(policy)
        assertTrue(policy is DefaultPasswordPolicy)
    }

    @Test
    fun `passwordPolicy 빈은 매번 새로운 인스턴스를 생성한다`() {
        // when
        val policy1 = config.passwordPolicy()
        val policy2 = config.passwordPolicy()

        // then - 싱글톤이 아닌 경우 다른 인스턴스
        assertTrue(policy1 !== policy2)
    }
}