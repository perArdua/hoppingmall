package com.hoppingmall.order.config

import com.hoppingmall.dlq.controller.DLQController
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.dlq.service.DLQQueryService
import com.hoppingmall.order.ops.CircuitBreakerAdminController
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.tracing.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("주문 서비스 운영·관리자 엔드포인트 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@WebMvcTest(controllers = [CircuitBreakerAdminController::class, DLQController::class])
@ActiveProfiles("loadtest")
@Import(SecurityConfig::class, AdminEndpointSecurityTest.TestConfig::class)
class AdminEndpointSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @MockitoBean
    private lateinit var dlqCommandService: DLQCommandService

    @MockitoBean
    private lateinit var dlqQueryService: DLQQueryService

    @MockitoBean
    private lateinit var tracer: Tracer

    @BeforeEach
    fun closeBreaker() {
        circuitBreakerRegistry.circuitBreaker(BREAKER).transitionToClosedState()
    }

    @Nested
    @DisplayName("/ops/circuitbreakers")
    inner class CircuitBreakerOps {

        @Test
        fun 인증_없이_서킷_브레이커를_모두_끄면_403이고_상태는_그대로다() {
            mockMvc.post("/ops/circuitbreakers/disable-all")
                .andExpect { status { isForbidden() } }

            assertThat(breakerState()).isEqualTo(CircuitBreaker.State.CLOSED)
        }

        @Test
        fun 인증_없이_서킷_브레이커를_초기화하면_403() {
            mockMvc.post("/ops/circuitbreakers/$BREAKER/reset")
                .andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_서킷_브레이커를_모두_끄면_403이고_상태는_그대로다() {
            mockMvc.post("/ops/circuitbreakers/disable-all") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }

            assertThat(breakerState()).isEqualTo(CircuitBreaker.State.CLOSED)
        }

        @Test
        fun ADMIN_역할로_서킷_브레이커를_모두_끌_수_있다() {
            mockMvc.post("/ops/circuitbreakers/disable-all") {
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }

            assertThat(breakerState()).isEqualTo(CircuitBreaker.State.DISABLED)
        }
    }

    @Nested
    @DisplayName("/api/v1/admin/dlq")
    inner class DlqAdmin {

        @Test
        fun 인증_없이_DLQ_통계를_조회하면_403() {
            mockMvc.get("/api/v1/admin/dlq/stats")
                .andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_DLQ_통계를_조회하면_403() {
            mockMvc.get("/api/v1/admin/dlq/stats") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_DLQ_메시지를_재처리하면_403이고_재처리하지_않는다() {
            mockMvc.post("/api/v1/admin/dlq/retry/message/1") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }

            verify(dlqCommandService, never()).retryDLQMessage(any())
        }

        @Test
        fun SELLER_역할로_처리된_DLQ_메시지를_삭제하면_403이고_삭제하지_않는다() {
            mockMvc.delete("/api/v1/admin/dlq/clear/payment") {
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isForbidden() } }

            verify(dlqCommandService, never()).clearProcessedDLQMessages(any())
        }

        @Test
        fun ADMIN_역할로_DLQ_통계를_조회할_수_있다() {
            mockMvc.get("/api/v1/admin/dlq/stats") {
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }
    }

    private fun breakerState(): CircuitBreaker.State = circuitBreakerRegistry.circuitBreaker(BREAKER).state

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun circuitBreakerRegistry(): CircuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
    }

    companion object {
        private const val BREAKER = "inventory-command"
    }
}
