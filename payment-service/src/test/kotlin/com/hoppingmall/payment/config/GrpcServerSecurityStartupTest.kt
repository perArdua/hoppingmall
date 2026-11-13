package com.hoppingmall.payment.config

import com.hoppingmall.common.config.GatewayHeaderAuthenticationFilter
import com.hoppingmall.common.config.InternalTokenFilter
import net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.security.core.userdetails.UserDetailsService

@DisplayName("gRPC 서버 보안 자동 구성과 함께 뜨는 payment 보안 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GrpcServerSecurityStartupTest {

    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                WebMvcAutoConfiguration::class.java,
                SecurityAutoConfiguration::class.java,
                UserDetailsServiceAutoConfiguration::class.java,
                GrpcServerSecurityAutoConfiguration::class.java
            )
        )
        .withPropertyValues("internal.service.token=test-internal-token")
        .withBean(GatewayHeaderAuthenticationFilter::class.java, { GatewayHeaderAuthenticationFilter() })
        .withBean(InternalTokenFilter::class.java, { InternalTokenFilter("test-internal-token") })
        .withUserConfiguration(SecurityConfig::class.java)

    @Test
    fun gRPC_서버_보안_자동_구성이_함께_있어도_payment_컨텍스트가_기동된다() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
        }
    }

    @Test
    fun gRPC_서버_보안_자동_구성이_함께_있어도_기본_사용자_계정을_만들지_않는다() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeanNamesForType(UserDetailsService::class.java)).isEmpty()
        }
    }
}
