package com.hoppingmall.mall.global.common.config

import jakarta.servlet.Filter
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.mockito.kotlin.whenever

class SecurityConfigTest {

    private val jwtAuthenticationFilter: Filter = mock()
    private val jwtAuthenticationEntryPoint: AuthenticationEntryPoint = mock()
    private val httpSecurity: HttpSecurity = mock()
    private val authConfig: AuthenticationConfiguration = mock()
    private val authManager: AuthenticationManager = mock()
    
    private val securityConfig = SecurityConfig(jwtAuthenticationFilter, jwtAuthenticationEntryPoint)

    @Test
    fun `passwordEncoder는 BCryptPasswordEncoder 인스턴스를 반환한다`() {
        // when
        val encoder: PasswordEncoder = securityConfig.passwordEncoder()

        // then
        assertNotNull(encoder)
        assertTrue(encoder is BCryptPasswordEncoder)
    }

    @Test
    fun `authenticationManager는 AuthenticationConfiguration에서 AuthenticationManager를 반환한다`() {
        // given
        whenever(authConfig.authenticationManager).thenReturn(authManager)

        // when
        val manager = securityConfig.authenticationManager(authConfig)

        // then
        assertNotNull(manager)
        assertTrue(manager === authManager)
    }

    @Test
    fun `passwordEncoder는 매번 새로운 BCryptPasswordEncoder 인스턴스를 생성한다`() {
        // when
        val encoder1 = securityConfig.passwordEncoder()
        val encoder2 = securityConfig.passwordEncoder()

        // then
        assertTrue(encoder1 !== encoder2)
        assertTrue(encoder1 is BCryptPasswordEncoder)
        assertTrue(encoder2 is BCryptPasswordEncoder)
    }
}

@SpringJUnitConfig
@WebMvcTest
@Import(SecurityConfig::class)
class SecurityConfigIntegrationTest {

    @MockBean
    private lateinit var jwtAuthenticationFilter: Filter

    @MockBean
    private lateinit var jwtAuthenticationEntryPoint: AuthenticationEntryPoint

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }
}