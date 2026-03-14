package com.hoppingmall.mall.support.config

import com.hoppingmall.mall.global.jwt.TokenProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.mockito.Mockito.mock

@Configuration
class TestMockConfig {
    @Bean
    fun tokenProvider(): TokenProvider = mock(TokenProvider::class.java)
}
