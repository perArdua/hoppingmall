package com.hoppingmall.mall.global.vo.password

import com.hoppingmall.mall.global.vo.password.policy.DefaultPasswordPolicy
import com.hoppingmall.mall.global.vo.password.service.PasswordCreator
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.security.crypto.password.PasswordEncoder

@DisplayName("PasswordCreator")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PasswordCreatorTest {

    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var passwordPolicy: DefaultPasswordPolicy
    private lateinit var passwordCreator: PasswordCreator

    @BeforeEach
    fun setUp() {
        passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder::class.java)
        passwordPolicy = DefaultPasswordPolicy()
        passwordCreator = PasswordCreator(passwordEncoder, passwordPolicy)
    }

    @Nested
    @DisplayName("encode")
    inner class Encode {
        @Test
        fun 비밀번호를_인코딩하면_해시된_값이_반환된다() {
            val rawPassword = "testPassword123"
            org.mockito.Mockito.`when`(passwordEncoder.encode(rawPassword)).thenReturn("encodedPassword123")
            
            val encodedPassword = passwordCreator.encode(rawPassword)

            assertNotNull(encodedPassword)
            assertNotNull(encodedPassword.value)
            assertEquals("encodedPassword123", encodedPassword.value)
        }
    }
}
