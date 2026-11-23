package com.hoppingmall.payment.config

import com.hoppingmall.payment.PaymentServiceApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@DisplayName("결제 서비스의 멱등성 모듈 등록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class IdempotencyModuleScanTest {

    @Test
    fun 애플리케이션이_멱등성_모듈의_빈을_읽는다() {
        val application = PaymentServiceApplication::class.java.getAnnotation(SpringBootApplication::class.java)

        assertThat(application.scanBasePackages).contains(IDEMPOTENCY_PACKAGE)
    }

    @Test
    fun 멱등성_레코드_엔티티와_저장소를_읽는다() {
        val entityScan = JpaConfig::class.java.getAnnotation(EntityScan::class.java)
        val repositories = JpaConfig::class.java.getAnnotation(EnableJpaRepositories::class.java)

        assertThat(entityScan.basePackages).contains(IDEMPOTENCY_PACKAGE)
        assertThat(repositories.basePackages).contains(IDEMPOTENCY_PACKAGE)
    }

    companion object {
        private const val IDEMPOTENCY_PACKAGE = "com.hoppingmall.idempotency"
    }
}
