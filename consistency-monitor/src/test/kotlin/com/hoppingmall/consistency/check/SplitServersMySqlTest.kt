package com.hoppingmall.consistency.check

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.monitor.ConsistencyMonitor
import com.hoppingmall.consistency.monitor.InvariantMetrics
import com.hoppingmall.consistency.source.SourceDatabases
import com.hoppingmall.consistency.support.FlywaySchemas
import com.hoppingmall.consistency.support.InvariantFixture
import com.hoppingmall.consistency.support.MonitorSources
import com.hoppingmall.consistency.support.MySqlContainerSupport
import com.hoppingmall.consistency.support.SecondMySqlContainerSupport
import com.hoppingmall.consistency.violation.ViolationRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

@DisplayName("주문·결제가 서로 다른 MySQL 서버에 있을 때의 점검 (컨테이너 두 개)")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SplitServersMySqlTest {

    private val properties = MonitorSources.properties(URLS, USERNAME, PASSWORD, chunkSize = 4, lookupBatchSize = 3)
    private val sources = SourceDatabases(properties)
    private val catalog = InvariantCatalog(DefaultResourceLoader(), properties)
    private val checks = InvariantChecks(catalog, sources, properties)
    private val definitions = catalog.definitions.associateBy { it.id }
    private val jdbc = URLS.mapValues { (_, url) -> JdbcTemplate(DriverManagerDataSource(url, USERNAME, PASSWORD)) }
    private val fixture = InvariantFixture { jdbc.getValue(it) }
    private val monitorDataSource = DriverManagerDataSource(MySqlContainerSupport.jdbcUrl("split_consistency_db"), USERNAME, PASSWORD)
    private val monitorJdbc = JdbcTemplate(monitorDataSource)

    @BeforeEach
    fun setUp() {
        fixture.truncateAll()
        monitorJdbc.execute("DROP TABLE IF EXISTS invariant_violations")
        monitorJdbc.execute("DROP TABLE IF EXISTS monitor_leases")
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(monitorDataSource)
    }

    @AfterEach
    fun tearDown() {
        sources.close()
    }

    private fun recentKeys(id: String): Set<String> =
        checks.recent(definitions.getValue(id)) { emptyList() }.rows.map { it.values.first().toString() }.toSet()

    private fun sweepKeys(id: String): Set<String> {
        val keys = mutableSetOf<String>()
        checks.sweep(definitions.getValue(id)) { rows -> rows.forEach { keys += it.values.first().toString() } }
        return keys
    }

    private fun insertConsistentDataset() {
        fixture.order(1, "PAID")
        fixture.payment(1, 1, "SUCCESS")
        fixture.orderItem(1, 1, "r-1")
        fixture.reservation(1, "r-1", "CONFIRMED", createdMinutesAgo = 5)
        fixture.order(2, "CANCELLED")
        fixture.payment(2, 2, "CANCELLED")
        fixture.orderItem(2, 2, "r-2")
        fixture.reservation(2, "r-2", "CANCELLED", createdMinutesAgo = 5)
        fixture.outboxEvent("payment", 1, "PaymentCompleted", "2", "payment", "PUBLISHED",
            """{"paymentId":2,"orderId":2,"transactionId":"tx-2"}""", processedSecondsAgo = 200)
        fixture.outboxEvent("payment", 2, "PaymentCancelled", "2", "payment-compensation", "PUBLISHED",
            """{"eventId":"pc-2","paymentId":2,"orderId":2}""", processedSecondsAgo = 190)
        fixture.sagaLog(2, "payment-completed-tx-2", 120)
        fixture.sagaLog(2, "pc-2", 60)
    }

    @ParameterizedTest
    @MethodSource("expectedViolations")
    fun 주문과_결제가_다른_서버여도_DB를_걸치는_점검이_어긋남을_잡는다(id: String, expected: Set<String>) {
        fixture.insertRecentDataset()

        assertThat(recentKeys(id)).isEqualTo(expected)
        assertThat(sweepKeys(id)).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("crossSourceIds")
    fun 어긋남이_없으면_DB를_걸치는_점검은_조용하다(id: String) {
        insertConsistentDataset()

        assertThat(recentKeys(id)).isEmpty()
        assertThat(sweepKeys(id)).isEmpty()
    }

    @Test
    fun 같은_토픽을_다른_소비자_그룹이_처리한_결제_취소와_취소_결과는_기록_순서가_바뀌어도_INV_10이_아니다() {
        fixture.order(3, "CANCELLED")
        fixture.payment(3, 3, "CANCELLED")
        fixture.outboxEvent("payment", 1, "PaymentCompleted", "3", "payment", "PUBLISHED",
            """{"paymentId":3,"orderId":3,"transactionId":"tx-3"}""", processedSecondsAgo = 200)
        fixture.outboxEvent("payment", 2, "PaymentCancelled", "3", "payment-compensation", "PUBLISHED",
            """{"eventId":"pc-3","paymentId":3,"orderId":3}""", processedSecondsAgo = 190)
        fixture.outboxEvent("payment", 3, "PaymentCancellationCompleted", "3", "payment-compensation", "PUBLISHED",
            """{"eventType":"PaymentCancellationCompleted","eventId":"cc-3","orderId":3,"paymentId":3}""", processedSecondsAgo = 189)
        fixture.sagaLog(3, "payment-completed-tx-3", 180)
        fixture.sagaLog(3, "cc-3", 150)
        fixture.sagaLog(3, "pc-3", 140)
        fixture.order(4, "CANCEL_FAILED")
        fixture.payment(4, 4, "SUCCESS")
        fixture.outboxEvent("payment", 4, "PaymentCompleted", "4", "payment", "PUBLISHED",
            """{"paymentId":4,"orderId":4,"transactionId":"tx-4"}""", processedSecondsAgo = 200)
        fixture.outboxEvent("payment", 5, "PaymentCancelled", "4", "payment-compensation", "PUBLISHED",
            """{"eventId":"pc-4","paymentId":4,"orderId":4}""", processedSecondsAgo = 190)
        fixture.outboxEvent("payment", 6, "PaymentCancellationFailed", "4", "payment-compensation", "PUBLISHED",
            """{"eventType":"PaymentCancellationFailed","eventId":"cf-4","orderId":4}""", processedSecondsAgo = 189)
        fixture.sagaLog(4, "payment-completed-tx-4", 180)
        fixture.sagaLog(4, "cf-4", 150)
        fixture.sagaLog(4, "pc-4", 140)

        assertThat(recentKeys("INV-10")).isEmpty()
        assertThat(sweepKeys("INV-10")).isEmpty()
    }

    @Test
    fun 결제_완료가_뒤이은_결제_취소와_취소_결과보다_늦게_기록되면_그대로_INV_10이다() {
        fixture.order(5, "CANCELLED")
        fixture.payment(5, 5, "CANCELLED")
        fixture.outboxEvent("payment", 1, "PaymentCompleted", "5", "payment", "PUBLISHED",
            """{"paymentId":5,"orderId":5,"transactionId":"tx-5"}""", processedSecondsAgo = 200)
        fixture.outboxEvent("payment", 2, "PaymentCancelled", "5", "payment-compensation", "PUBLISHED",
            """{"eventId":"pc-5","paymentId":5,"orderId":5}""", processedSecondsAgo = 190)
        fixture.outboxEvent("payment", 3, "PaymentCancellationCompleted", "5", "payment-compensation", "PUBLISHED",
            """{"eventType":"PaymentCancellationCompleted","eventId":"cc-5","orderId":5,"paymentId":5}""", processedSecondsAgo = 189)
        fixture.sagaLog(5, "payment-completed-tx-5", 100)
        fixture.sagaLog(5, "pc-5", 150)
        fixture.sagaLog(5, "cc-5", 140)

        assertThat(recentKeys("INV-10")).containsExactlyInAnyOrder("5:1>2", "5:1>3")
        assertThat(sweepKeys("INV-10")).containsExactlyInAnyOrder("5:1>2", "5:1>3")
    }

    @ParameterizedTest
    @MethodSource("crossSourceIds")
    fun 표가_비어_있으면_전체_훑기는_아무것도_하지_않는다(id: String) {
        assertThat(sweepKeys(id)).isEmpty()
        assertThat(recentKeys(id)).isEmpty()
    }

    @Test
    fun 모니터가_다른_서버의_어긋남을_기록하고_고쳐지면_스스로_닫으며_오래된_어긋남은_전체_훑기가_잡는다() {
        insertConsistentDataset()
        fixture.order(801, "CANCELLED")
        fixture.payment(901, 801, "SUCCESS")
        val registry = SimpleMeterRegistry()
        val repository = ViolationRepository(monitorJdbc)
        val monitor = ConsistencyMonitor(
            catalog, properties, checks, repository, InvariantMetrics(registry, catalog, properties),
            ObjectMapper().registerModule(JavaTimeModule())
        )

        monitor.runCycle()

        assertThat(registry.find("invariant.check.errors").counters().sumOf { it.count() }).isZero()
        assertThat(repository.findOpen("INV-1").map { it.entityKey }).containsExactly("801")
        assertThat(repository.findOpen("INV-1").single().confirmedAt).isNotNull()

        jdbc.getValue("payment").update("UPDATE payments SET status = 'CANCELLED', updated_at = NOW(6) WHERE id = 901")
        fixture.order(802, "CANCELLED", updatedMinutesAgo = InvariantFixture.STALE)
        fixture.payment(902, 802, "SUCCESS", updatedMinutesAgo = InvariantFixture.STALE)
        monitor.runCycle()

        assertThat(repository.findOpen("INV-1")).isEmpty()
        assertThat(repository.findAll(false).single { it.entityKey == "801" }.ackedBy).isEqualTo(ConsistencyMonitor.SYSTEM_ACTOR)

        monitor.runFullSweep()
        monitor.runCycle()

        assertThat(repository.findOpen("INV-1").map { it.entityKey }).containsExactly("802")
        assertThat(registry.find("invariant.check.errors").counters().sumOf { it.count() }).isZero()
    }

    companion object {
        private val USERNAME = MySqlContainerSupport.username
        private val PASSWORD = MySqlContainerSupport.password

        private val URLS: Map<String, String> by lazy {
            check(SecondMySqlContainerSupport.username == USERNAME && SecondMySqlContainerSupport.password == PASSWORD)
            mapOf(
                "order" to MySqlContainerSupport.jdbcUrl("split_order_db"),
                "user" to MySqlContainerSupport.jdbcUrl("split_user_db"),
                "notification" to MySqlContainerSupport.jdbcUrl("split_notification_db"),
                "payment" to SecondMySqlContainerSupport.jdbcUrl("split_payment_db"),
                "product" to SecondMySqlContainerSupport.jdbcUrl("split_product_db")
            )
        }

        @JvmStatic
        fun crossSourceIds(): List<String> = listOf("INV-1", "INV-1R", "INV-3a", "INV-3b", "INV-10")

        @JvmStatic
        fun expectedViolations(): List<Array<Any>> = listOf(
            arrayOf("INV-1", setOf("101")),
            arrayOf("INV-1R", setOf("111", "113")),
            arrayOf("INV-3a", setOf("r-131")),
            arrayOf("INV-3b", setOf("r-134", "r-141")),
            arrayOf("INV-10", setOf("171:1>2", "173:5>6"))
        )

        @JvmStatic
        @BeforeAll
        fun migrate() {
            URLS.forEach { (source, url) ->
                FlywaySchemas.migrate(url, USERNAME, PASSWORD, FlywaySchemas.serviceOfSource.getValue(source))
            }
        }
    }
}
