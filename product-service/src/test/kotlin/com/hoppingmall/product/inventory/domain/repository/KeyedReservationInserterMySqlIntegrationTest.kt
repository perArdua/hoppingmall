package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.support.MySqlContainerSupport
import com.hoppingmall.product.support.TestCacheConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
@DisplayName("요청 키 예약 INSERT MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class KeyedReservationInserterMySqlIntegrationTest {

    @Autowired
    private lateinit var inserter: KeyedReservationInserter

    @Autowired
    private lateinit var repository: InventoryReservationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanTable() {
        jdbcTemplate.update("DELETE FROM inventory_reservations")
    }

    @Test
    fun 연결_시간대가_JVM과_달라도_JPA_저장과_같은_값을_넣는다() {
        listOf(
            LocalDateTime.of(2026, 11, 22, 23, 59, 59, 123_456_000),
            LocalDateTime.of(2026, 11, 22, 23, 59, 59, 999_999_700)
        ).forEachIndexed { index, at ->
            val viaJpa = reservation("jpa-$index", at)
            val viaJdbc = reservation("jdbc-$index", at)

            repository.saveAndFlush(viaJpa)
            inserter.insert(viaJdbc)

            assertThat(storedColumns(viaJdbc.reservationId)).isEqualTo(storedColumns(viaJpa.reservationId))
        }
    }

    @Test
    fun 넣은_행을_JPA로_읽으면_같은_예약이다() {
        val saved = reservation("key-1", LocalDateTime.of(2026, 11, 22, 10, 0, 0, 123_456_000))

        inserter.insert(saved)

        val found = repository.findByRequestKey("key-1").single()
        assertThat(found.id).isNotNull()
        assertThat(found).usingRecursiveComparison().ignoringFields("id").isEqualTo(saved)
    }

    @Test
    fun 바깥_트랜잭션이_롤백되면_넣은_행도_사라진다() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            inserter.insert(reservation("key-rollback", LocalDateTime.of(2026, 11, 22, 10, 0)))
            status.setRollbackOnly()
        }

        assertThat(repository.findByRequestKey("key-rollback")).isEmpty()
    }

    private fun reservation(requestKey: String, at: LocalDateTime): InventoryReservation =
        InventoryReservation.create(6101L, 3, at.plusMinutes(10), at, requestKey).apply { status = ReservationStatus.RESERVED }

    private fun storedColumns(reservationId: String): Map<String, Any> =
        jdbcTemplate.queryForMap(
            """
            SELECT product_id, quantity, status, CAST(created_at AS CHAR) AS created_at,
                   CAST(expires_at AS CHAR) AS expires_at, CAST(updated_at AS CHAR) AS updated_at
            FROM inventory_reservations WHERE reservation_id = ?
            """.trimIndent(),
            reservationId
        )

    companion object {
        private const val DATABASE = "product_keyed_insert_test"
        private const val SESSION_TIME_ZONE_QUERY = "&connectionTimeZone=%2B02%3A00&forceConnectionTimeZoneToSession=true"

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, DATABASE)
            registry.add("spring.datasource.url") { MySqlContainerSupport.jdbcUrl(DATABASE) + SESSION_TIME_ZONE_QUERY }
        }
    }
}
