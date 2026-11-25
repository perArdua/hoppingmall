package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.ProductServiceApplication
import com.hoppingmall.product.internal.InternalInventoryController
import com.hoppingmall.product.internal.InternalInventoryController.ReserveRequest
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryBucket
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.support.MySqlContainerSupport
import com.hoppingmall.reservation.grpc.ReservationLinkRequest
import com.hoppingmall.reservation.grpc.ReservationLinkResponse
import com.hoppingmall.reservation.grpc.ReservationLinkServiceGrpc
import io.grpc.Server
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.classreading.MetadataReaderFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.DriverManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@DisplayName("데이터가 있는 DB를 배포 프로필의 재고 스위치로 켠 기동 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class BucketModeStartupMySqlIntegrationTest {

    private val orderLinks = FakeOrderLinkService()
    private val orderServerName = InProcessServerBuilder.generateName()
    private lateinit var orderServer: Server

    @BeforeEach
    fun setUp() {
        orderServer = InProcessServerBuilder.forName(orderServerName).addService(orderLinks).build().start()
        DriverManager.getConnection(MySqlContainerSupport.jdbcUrl("mysql"), MySqlContainerSupport.username, MySqlContainerSupport.password)
            .use { it.createStatement().execute("DROP DATABASE IF EXISTS $DATABASE") }
    }

    @AfterEach
    fun tearDown() {
        orderServer.shutdownNow()
        orderServer.awaitTermination(10, TimeUnit.SECONDS)
    }

    @Test
    fun docker와_k8s_프로필은_버킷_모드와_링크_확인을_함께_켠다() {
        val docker = inventorySwitches("application-docker.yml")

        assertThat(docker)
            .containsEntry("inventory.mode", "bucket")
            .containsEntry("inventory.link-check.enabled", "true")
        assertThat(inventorySwitches("application-k8s.yml")).isEqualTo(docker)
    }

    @Test
    fun 단일_모드로_쌓인_재고와_예약이_있는_DB를_모드_변경_절차대로_배포_프로필_스위치로_켜면_버킷이_채워지고_예약_확정_취소_만료가_된다() {
        val pendingFromSingleMode = boot(mapOf("inventory.mode" to "single")).use { context ->
            val inventories = context.getBean(InventoryRepository::class.java)
            inventories.saveAndFlush(Inventory.create(productId = FIRST, stockQuantity = 10))
            inventories.saveAndFlush(Inventory.create(productId = SECOND, stockQuantity = 5))
            context.getBean(InventoryCommandService::class.java).batchReserveStock(listOf(FIRST to 2)).getValue(FIRST)
        }
        recordBucketModeAsProcedure()

        boot(inventorySwitches("application-docker.yml") + FAST_LINK_CHECK).use { context ->
            val jdbc = context.getBean(JdbcTemplate::class.java)
            val controller = context.getBean(InternalInventoryController::class.java)

            assertThat(context.getBeansOfType(ReservationLinkCheckJob::class.java)).hasSize(1)
            assertThat(context.getBeansOfType(ReservationExpiryScheduler::class.java)).isEmpty()
            assertThat(bucketStocks(jdbc, FIRST)).hasSize(8)
            assertThat(bucketStocks(jdbc, FIRST).sum()).isEqualTo(8)
            assertThat(bucketStocks(jdbc, SECOND)).hasSize(8)
            assertThat(bucketStocks(jdbc, SECOND).sum()).isEqualTo(5)

            val reserveStartedAt = LocalDateTime.now()
            val paid = controller.batchReserveStock(
                listOf(ReserveRequest(FIRST, 3), ReserveRequest(SECOND, 1)), BUDGET_MS, null
            ).body!!
            val abandoned = controller.batchReserveStock(listOf(ReserveRequest(FIRST, 1)), BUDGET_MS, null).body!!.getValue(FIRST)
            assertThat(Duration.between(reserveStartedAt, expiresAt(jdbc, paid.getValue(FIRST))))
                .isLessThan(Duration.ofMillis(BUDGET_MS + ReserveDeadline.EXPIRY_GRACE_MS + 1_000))

            controller.cancelReservation(paid.getValue(SECOND))
            assertThat(status(jdbc, paid.getValue(SECOND))).isEqualTo("CANCELLED")
            assertThat(bucketStocks(jdbc, SECOND).sum()).isEqualTo(5)

            orderLinks.linked = setOf(paid.getValue(FIRST))
            makeExpired(jdbc, paid.getValue(FIRST), abandoned, pendingFromSingleMode)
            awaitStatuses(
                jdbc,
                mapOf(paid.getValue(FIRST) to "CLAIMED", abandoned to "EXPIRED", pendingFromSingleMode to "EXPIRED")
            )

            assertThat(orderLinks.askedIds()).contains(paid.getValue(FIRST), abandoned, pendingFromSingleMode)
            assertThat(Duration.between(LocalDateTime.now(), expiresAt(jdbc, paid.getValue(FIRST))))
                .isBetween(Duration.ofHours(23), Duration.ofHours(25))
            assertThat(controller.confirmReservations(listOf(paid.getValue(FIRST))).body!!.confirmed).isTrue()
            assertThat(status(jdbc, paid.getValue(FIRST))).isEqualTo("CONFIRMED")
            assertThat(bucketStocks(jdbc, FIRST).sum()).isEqualTo(7)
            assertThat(inventoryRowStock(jdbc, FIRST)).isEqualTo(8)
        }
    }

    @Test
    fun 단일_모드로_쓰던_DB를_절차_없이_bucket_인스턴스로_켜면_기동이_실패하고_버킷을_만들지_않는다() {
        boot(mapOf("inventory.mode" to "single")).use { context ->
            context.getBean(InventoryRepository::class.java).saveAndFlush(Inventory.create(productId = FIRST, stockQuantity = 10))
        }

        assertThatThrownBy { boot(inventorySwitches("application-docker.yml")).close() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("DB=single")
            .hasMessageContaining("이 인스턴스=bucket(bucket.count=8)")
            .hasMessageContaining(InventoryModeGuard.PROCEDURE)

        assertThat(queryForString("SELECT COUNT(*) FROM inventory_buckets")).isEqualTo("0")
    }

    @Test
    fun 기록이_없는_DB에_inventories_재고만_있으면_bucket_인스턴스는_기록하지_않고_기동이_실패하고_버킷을_만들지_않는다() {
        boot(mapOf("inventory.mode" to "single")).use { context ->
            context.getBean(InventoryRepository::class.java).saveAndFlush(Inventory.create(productId = FIRST, stockQuantity = 10))
        }
        executeSql("DELETE FROM inventory_runtime_mode")

        assertThatThrownBy { boot(inventorySwitches("application-docker.yml")).close() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("inventories에만 재고가 있어")
            .hasMessageContaining("이 인스턴스=bucket(bucket.count=8)")
            .hasMessageContaining(InventoryModeGuard.PROCEDURE)

        assertThat(queryForString("SELECT COUNT(*) FROM inventory_buckets")).isEqualTo("0")
        assertThat(queryForString("SELECT COUNT(*) FROM inventory_runtime_mode")).isEqualTo("0")
    }

    @Test
    fun 기록_없이_버킷_행이_있는_DB를_bucket_인스턴스로_켜면_bucket과_버킷_수를_기록하고_뜬다() {
        boot(mapOf("inventory.mode" to "single")).use { context ->
            context.getBean(InventoryRepository::class.java).saveAndFlush(Inventory.create(productId = FIRST, stockQuantity = 10))
        }
        executeSql("DELETE FROM inventory_runtime_mode")
        (0 until 8).forEach { bucketNo ->
            executeSql(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) " +
                    "VALUES ($FIRST, $bucketNo, ${InventoryBucket.shareOf(10, 8, bucketNo)})"
            )
        }

        boot(inventorySwitches("application-docker.yml")).use { context ->
            assertThat(context.isActive).isTrue()
        }

        assertThat(queryForString("SELECT CONCAT(mode, '/', bucket_count) FROM inventory_runtime_mode WHERE id = 1")).isEqualTo("bucket/8")
    }

    private fun recordBucketModeAsProcedure() {
        executeSql("UPDATE inventory_runtime_mode SET mode = 'bucket', bucket_count = 8, updated_at = NOW(6) WHERE id = 1")
    }

    private fun executeSql(sql: String) {
        databaseConnection().use { it.createStatement().executeUpdate(sql) }
    }

    private fun queryForString(sql: String): String? =
        databaseConnection().use { connection ->
            connection.createStatement().executeQuery(sql).use { rs ->
                rs.next()
                rs.getString(1)
            }
        }

    private fun databaseConnection() =
        DriverManager.getConnection(MySqlContainerSupport.jdbcUrl(DATABASE), MySqlContainerSupport.username, MySqlContainerSupport.password)

    private fun boot(switches: Map<String, String>): ConfigurableApplicationContext =
        SpringApplicationBuilder(ProductServiceApplication::class.java)
            .profiles("cache-disabled")
            .initializers(ApplicationContextInitializer<ConfigurableApplicationContext> {
                it.beanFactory.registerSingleton(TestClassExcludeFilter::class.java.name, TestClassExcludeFilter())
            })
            .run(*(bootProperties() + switches).map { "--${it.key}=${it.value}" }.toTypedArray())

    private fun bootProperties(): Map<String, String> = mapOf(
        "spring.datasource.url" to MySqlContainerSupport.jdbcUrl(DATABASE),
        "spring.datasource.username" to MySqlContainerSupport.username,
        "spring.datasource.password" to MySqlContainerSupport.password,
        "spring.datasource.driver-class-name" to "com.mysql.cj.jdbc.Driver",
        "spring.datasource.hikari.maximum-pool-size" to "5",
        "spring.datasource.hikari.minimum-idle" to "0",
        "spring.jpa.database-platform" to "org.hibernate.dialect.MySQLDialect",
        "spring.autoconfigure.exclude" to REDIS_AUTO_CONFIGURATIONS.joinToString(","),
        "spring.kafka.bootstrap-servers" to UNREACHABLE_KAFKA,
        "spring.kafka.consumer.bootstrap-servers" to UNREACHABLE_KAFKA,
        "server.port" to "0",
        "grpc.server.port" to "-1",
        "grpc.client.order-service.address" to "in-process:$orderServerName",
        "services.order-service.url" to "http://127.0.0.1:1",
        "dlq.scheduler.enabled" to "false",
        "inventory.mode-guard.recheck-ms" to "3600000"
    )

    private fun inventorySwitches(resource: String): Map<String, String> =
        YamlPropertySourceLoader().load(resource, ClassPathResource(resource))
            .flatMap { source ->
                (source as EnumerablePropertySource<*>).propertyNames.map { it to source.getProperty(it).toString() }
            }
            .filter { it.first.startsWith("inventory.") }
            .toMap()

    private fun makeExpired(jdbc: JdbcTemplate, vararg reservationIds: String) {
        reservationIds.forEach {
            jdbc.update(
                "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ?",
                LocalDateTime.now().minusMinutes(1), it
            )
        }
    }

    private fun awaitStatuses(jdbc: JdbcTemplate, expected: Map<String, String>) {
        val deadline = System.currentTimeMillis() + AWAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (expected.all { (rid, status) -> status(jdbc, rid) == status }) return
            Thread.sleep(100)
        }
        assertThat(expected.mapValues { status(jdbc, it.key) }).isEqualTo(expected)
    }

    private fun status(jdbc: JdbcTemplate, reservationId: String): String =
        jdbc.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun expiresAt(jdbc: JdbcTemplate, reservationId: String): LocalDateTime =
        jdbc.queryForObject("SELECT expires_at FROM inventory_reservations WHERE reservation_id = ?", LocalDateTime::class.java, reservationId)!!

    private fun bucketStocks(jdbc: JdbcTemplate, productId: Long): List<Int> =
        jdbc.queryForList(
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? ORDER BY bucket_no",
            Int::class.java,
            productId
        )

    private fun inventoryRowStock(jdbc: JdbcTemplate, productId: Long): Int =
        jdbc.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    private class TestClassExcludeFilter : TypeExcludeFilter() {
        private val testClassesRoot = BucketModeStartupMySqlIntegrationTest::class.java.protectionDomain.codeSource.location.toString()

        override fun match(metadataReader: MetadataReader, metadataReaderFactory: MetadataReaderFactory): Boolean =
            metadataReader.resource.url.toString().startsWith(testClassesRoot)

        override fun equals(other: Any?): Boolean = other is TestClassExcludeFilter

        override fun hashCode(): Int = javaClass.hashCode()
    }

    private class FakeOrderLinkService : ReservationLinkServiceGrpc.ReservationLinkServiceImplBase() {
        val asked = CopyOnWriteArrayList<List<String>>()

        @Volatile
        var linked: Set<String> = emptySet()

        fun askedIds(): List<String> = asked.flatten()

        override fun checkReservationLinks(request: ReservationLinkRequest, observer: StreamObserver<ReservationLinkResponse>) {
            val ids = request.reservationIdsList.toList()
            asked.add(ids)
            observer.onNext(
                ReservationLinkResponse.newBuilder()
                    .addAllLinked(ids.filter { it in linked })
                    .addAllAborted(ids.filterNot { it in linked })
                    .build()
            )
            observer.onCompleted()
        }
    }

    companion object {
        private const val DATABASE = "product_bucket_startup_test"
        private const val FIRST = 9451L
        private const val SECOND = 9452L
        private const val BUDGET_MS = 5_000L
        private const val AWAIT_MS = 20_000L
        private const val UNREACHABLE_KAFKA = "127.0.0.1:1"
        private val FAST_LINK_CHECK = mapOf("inventory.link-check.interval-ms" to "200")
        private val REDIS_AUTO_CONFIGURATIONS = listOf(
            "org.redisson.spring.starter.RedissonAutoConfigurationV2",
            "org.redisson.spring.starter.RedissonAutoConfigurationV4",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        )
    }
}
