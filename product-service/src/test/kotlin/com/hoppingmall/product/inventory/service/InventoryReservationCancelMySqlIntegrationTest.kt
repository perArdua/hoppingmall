package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("재고 예약 취소 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class InventoryReservationCancelMySqlIntegrationTest {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM inventory_reservations")
        jdbcTemplate.update("DELETE FROM inventories")
    }

    @Test
    fun 같은_예약을_동시에_여러_번_취소해도_재고는_한_번만_복원된다() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 10))
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        assertThat(stock()).isEqualTo(7)

        val threads = 8
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(threads) {
            executor.submit {
                start.await()
                runCatching { inventoryCommandService.cancelReservation(reservationId) }.onFailure { errors.add(it) }
            }
        }
        start.countDown()
        executor.shutdown()
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue()

        assertThat(errors).isEmpty()
        assertThat(stock()).isEqualTo(10)
        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELLED")
    }

    private fun stock(): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, PRODUCT_ID)!!

    private fun reservationStatus(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    companion object {
        private const val PRODUCT_ID = 1001L

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "product_reservation_cancel")
        }
    }
}
