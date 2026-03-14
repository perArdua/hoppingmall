package com.hoppingmall.mall.global.common.config

import com.hoppingmall.mall.global.vo.password.policy.DefaultPasswordPolicy
import com.hoppingmall.mall.global.vo.password.policy.PasswordPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PasswordPolicyConfig {

    @Bean
    fun passwordPolicy(): PasswordPolicy {
        return DefaultPasswordPolicy()
    }
}
