package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.exception.InventoryBusyException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("InventoryReservationGate 단위 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryReservationGateTest {

    private val meterRegistry = SimpleMeterRegistry()

    @Test
    fun 허가_수_기본값은_버킷_수다() {
        assertThat(InventoryReservationGate(meterRegistry, 8, 0).permits).isEqualTo(8)
        assertThat(InventoryReservationGate(meterRegistry, 8, 3).permits).isEqualTo(3)
    }

    @Test
    fun 블록이_끝나면_허가를_반납한다() {
        val gate = InventoryReservationGate(meterRegistry, 2, 0)

        val result = gate.withPermits(listOf(1L)) { gate.minAvailable() }

        assertThat(result).isEqualTo(1)
        assertThat(gate.minAvailable()).isEqualTo(2)
    }

    @Test
    fun 블록이_예외를_던져도_허가를_반납한다() {
        val gate = InventoryReservationGate(meterRegistry, 2, 0)

        assertThatThrownBy { gate.withPermits(listOf(1L)) { throw IllegalStateException("boom") } }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(gate.minAvailable()).isEqualTo(2)
    }

    @Test
    fun 허가가_없으면_429_예외를_던지고_먼저_얻은_허가를_돌려준다() {
        val gate = InventoryReservationGate(meterRegistry, 1, 0)

        gate.withPermits(listOf(2L)) {
            assertThatThrownBy { gate.withPermits(listOf(1L, 2L)) { } }
                .isInstanceOf(InventoryBusyException::class.java)
            assertThat(gate.withPermits(listOf(1L)) { "ok" }).isEqualTo("ok")
        }
        assertThat(meterRegistry.counter("inventory.reserve.rejected", "reason", "queue_full").count()).isEqualTo(1.0)
    }

    @Test
    fun 상품이_없을_때_가용_허가는_설정값이다() {
        val gate = InventoryReservationGate(meterRegistry, 8, 0)

        assertThat(gate.minAvailable()).isEqualTo(8)
        assertThat(meterRegistry.get("inventory.semaphore.available").gauge().value()).isEqualTo(8.0)
    }
}
