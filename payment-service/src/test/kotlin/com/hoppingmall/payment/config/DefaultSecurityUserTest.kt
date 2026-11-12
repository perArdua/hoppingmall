package com.hoppingmall.payment.config

import com.hoppingmall.payment.coupon.controller.CouponController
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.coupon.service.CouponQueryService
import io.micrometer.tracing.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.test.context.bean.override.mockito.MockitoBean

@DisplayName("payment 기본 보안 사용자")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(OutputCaptureExtension::class)
@WebMvcTest(controllers = [CouponController::class])
@Import(SecurityConfig::class)
class DefaultSecurityUserTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @MockitoBean
    private lateinit var couponCommandService: CouponCommandService

    @MockitoBean
    private lateinit var couponQueryService: CouponQueryService

    @MockitoBean
    private lateinit var tracer: Tracer

    @Test
    fun 기본_사용자_계정을_만들지_않는다() {
        assertThat(applicationContext.getBeanNamesForType(UserDetailsService::class.java)).isEmpty()
    }

    @Test
    fun 기동_로그에_생성된_보안_비밀번호를_남기지_않는다(output: CapturedOutput) {
        assertThat(output.all.contains("generated security password")).isFalse()
    }
}
