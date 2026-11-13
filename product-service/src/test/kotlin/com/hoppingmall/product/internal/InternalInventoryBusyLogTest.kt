package com.hoppingmall.product.internal

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.InventoryQueryService
import com.hoppingmall.product.inventory.service.InventoryReservationGate
import com.hoppingmall.product.support.TestCacheConfig
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@DisplayName("재고 예약 혼잡 응답 로그·지표 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class InternalInventoryBusyLogTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var reservationGate: InventoryReservationGate

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @MockitoBean
    private lateinit var inventoryCommandService: InventoryCommandService

    @MockitoBean
    private lateinit var inventoryQueryService: InventoryQueryService

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    private val appender = ListAppender<ILoggingEvent>()
    private val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    @BeforeEach
    fun setUp() {
        appender.start()
        rootLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        rootLogger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun 예약_진입_상한에_걸린_429_응답은_WARN_로그를_남기지_않는다() {
        whenever(inventoryCommandService.reserveStock(PRODUCT_ID, 1)).thenAnswer { occupy(reservationGate.permits + 1) }

        repeat(REQUESTS) {
            mockMvc.post("/internal/api/v1/inventory/$PRODUCT_ID/reserve") {
                param("quantity", "1")
                header("X-Internal-Token", internalToken)
            }.andExpect {
                status { isTooManyRequests() }
                jsonPath("$.code") { value("INV005") }
            }
        }

        assertThat(busyLogsAtWarnOrAbove()).isEmpty()
    }

    @Test
    fun 예약_진입_상한에_걸린_거절은_요청마다_거절_지표로_센다() {
        whenever(inventoryCommandService.reserveStock(PRODUCT_ID, 1)).thenAnswer { occupy(reservationGate.permits + 1) }
        val before = rejectedCount()

        repeat(REQUESTS) {
            mockMvc.post("/internal/api/v1/inventory/$PRODUCT_ID/reserve") {
                param("quantity", "1")
                header("X-Internal-Token", internalToken)
            }.andExpect {
                status { isTooManyRequests() }
            }
        }

        assertThat(rejectedCount() - before).isEqualTo(REQUESTS.toDouble())
    }

    private fun occupy(depth: Int): String =
        if (depth == 0) RESERVATION_ID else reservationGate.withPermits(listOf(PRODUCT_ID)) { occupy(depth - 1) }

    private fun busyLogsAtWarnOrAbove(): List<ILoggingEvent> =
        appender.list.filter { it.level.isGreaterOrEqual(Level.WARN) && it.formattedMessage.contains("INV005") }

    private fun rejectedCount(): Double =
        meterRegistry.find("inventory.reserve.rejected").tag("reason", "queue_full").counter()?.count() ?: 0.0

    companion object {
        private const val PRODUCT_ID = 9_871L
        private const val RESERVATION_ID = "busy-log-reservation"
        private const val REQUESTS = 3
    }
}
