package com.hoppingmall.order.port

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.test.util.ReflectionTestUtils
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@DisplayName("재고 예약 예산과 읽기 제한 시간")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpInventoryCommandAdapterReserveBudgetTimeoutTest {

    private lateinit var server: HttpServer

    private val receivedBudgets = CopyOnWriteArrayList<String?>()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/internal/api/v1/inventory/batch-reserve") { exchange ->
            receivedBudgets.add(exchange.requestHeaders.getFirst(HttpInventoryCommandAdapter.RESERVE_BUDGET_HEADER))
            exchange.requestBody.readAllBytes()
            Thread.sleep(HttpInventoryCommandAdapter.RESERVE_BUDGET_MS + PRODUCT_RESPONSE_OVERHEAD_MS)
            val body = """{"$PRODUCT_ID":"rsv-1"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun 상품이_예약_예산을_다_쓰고_답해도_order가_읽기_제한_시간으로_먼저_끊지_않는다() {
        val adapter = HttpInventoryCommandAdapter("http://127.0.0.1:${server.address.port}", RestTemplateBuilder())
        ReflectionTestUtils.setField(adapter, "budgetHeaderEnabled", true)

        val reservations = adapter.batchReserveStock(listOf(PRODUCT_ID to 1))

        assertThat(reservations).containsEntry(PRODUCT_ID, "rsv-1")
        assertThat(receivedBudgets).containsExactly(HttpInventoryCommandAdapter.RESERVE_BUDGET_MS.toString())
    }

    companion object {
        private const val PRODUCT_ID = 100L
        private const val PRODUCT_RESPONSE_OVERHEAD_MS = 100L
    }
}
