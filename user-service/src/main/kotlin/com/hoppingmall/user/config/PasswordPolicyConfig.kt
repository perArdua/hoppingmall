package com.hoppingmall.user.config

import com.hoppingmall.user.common.vo.DefaultPasswordPolicy
import com.hoppingmall.user.common.vo.PasswordPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PasswordPolicyConfig {

    @Bean
    fun passwordPolicy(): PasswordPolicy = DefaultPasswordPolicy()
}
