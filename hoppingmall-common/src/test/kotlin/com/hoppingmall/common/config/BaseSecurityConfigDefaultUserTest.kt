package com.hoppingmall.common.config

import net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class CommonOnlySecurityConfig : BaseSecurityConfig(GatewayHeaderAuthenticationFilter(), null) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = configureBase(http).build()
}

@Configuration
@EnableWebSecurity
class OwnAuthenticationManagerSecurityConfig : BaseSecurityConfig(GatewayHeaderAuthenticationFilter(), null) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = configureBase(http).build()

    @Bean
    fun authenticationManager(): AuthenticationManager = AuthenticationManager {
        throw BadCredentialsException("service")
    }
}

@DisplayName("공통 보안 설정의 기본 보안 사용자")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(OutputCaptureExtension::class)
class BaseSecurityConfigDefaultUserTest {

    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                WebMvcAutoConfiguration::class.java,
                SecurityAutoConfiguration::class.java,
                UserDetailsServiceAutoConfiguration::class.java
            )
        )

    @Test
    fun 공통_보안_설정만_쓰는_서비스는_기본_사용자_계정을_만들지_않는다() {
        contextRunner.withUserConfiguration(CommonOnlySecurityConfig::class.java).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeanNamesForType(UserDetailsService::class.java)).isEmpty()
        }
    }

    @Test
    fun 공통_보안_설정만_쓰는_서비스는_기동_로그에_생성된_보안_비밀번호를_남기지_않는다(output: CapturedOutput) {
        contextRunner.withUserConfiguration(CommonOnlySecurityConfig::class.java).run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(output.all.contains("generated security password")).isFalse()
    }

    @Test
    fun gRPC_서버_보안_자동_구성이_함께_있어도_공통_보안_설정만_쓰는_서비스가_기동된다() {
        contextRunner
            .withConfiguration(AutoConfigurations.of(GrpcServerSecurityAutoConfiguration::class.java))
            .withUserConfiguration(CommonOnlySecurityConfig::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBeanNamesForType(AuthenticationManager::class.java)).isEmpty()
            }
    }

    @Test
    fun 서비스에_인증_관리자_빈이_있으면_공통_빈을_만들지_않고_서비스_빈만_남긴다() {
        contextRunner.withUserConfiguration(OwnAuthenticationManagerSecurityConfig::class.java).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeanNamesForType(AuthenticationManager::class.java))
                .containsExactly("authenticationManager")
            assertThat(context.getBeanNamesForType(AuthenticationProvider::class.java)).isEmpty()
            assertThat(context.getBeanNamesForType(UserDetailsService::class.java)).isEmpty()
        }
    }
}
