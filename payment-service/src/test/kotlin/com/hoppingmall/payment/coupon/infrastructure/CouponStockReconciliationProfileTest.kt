package com.hoppingmall.payment.coupon.infrastructure

import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource

@DisplayName("프로필별 쿠폰 정합성 스윕 등록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CouponStockReconciliationProfileTest {

    private fun load(file: String): PropertySource<*> =
        YamlPropertySourceLoader().load(file, ClassPathResource(file)).single()

    private fun contextRunner(vararg files: String) = ApplicationContextRunner()
        .withInitializer { context -> files.forEach { context.environment.propertySources.addFirst(load(it)) } }
        .withBean(CouponRepository::class.java, { mock<CouponRepository>() })
        .withBean(UserCouponRepository::class.java, { mock<UserCouponRepository>() })
        .withBean(CouponStockRedisRepository::class.java, { mock<CouponStockRedisRepository>() })
        .withUserConfiguration(CouponStockReconciliationScheduler::class.java)

    @ParameterizedTest
    @ValueSource(strings = ["application-docker.yml", "application-k8s.yml"])
    fun 배포_프로필에서는_스윕이_등록된다(file: String) {
        contextRunner("application.yml", file).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(CouponStockReconciliationScheduler::class.java)
        }
    }

    @Test
    fun 기본_프로필에서는_스윕이_등록되지_않는다() {
        contextRunner("application.yml").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(CouponStockReconciliationScheduler::class.java)
        }
    }
}
