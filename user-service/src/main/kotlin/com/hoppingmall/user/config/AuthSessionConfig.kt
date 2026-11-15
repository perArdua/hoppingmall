package com.hoppingmall.user.config

import com.hoppingmall.user.auth.SessionProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(SessionProperties::class)
class AuthSessionConfig
