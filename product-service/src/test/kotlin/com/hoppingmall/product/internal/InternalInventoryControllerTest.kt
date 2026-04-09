package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.inventory.service.InventoryCommandService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@DisplayName("InternalInventoryController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InternalInventoryControllerTest {

    @Mock
    private lateinit var inventoryCommandService: InventoryCommandService

    @InjectMocks
    private lateinit var controller: InternalInventoryController

    @Test
    fun 재고를_차감한다() {
        val result = controller.decreaseStock(1L, 5)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        verify(inventoryCommandService).decreaseStock(1L, 5)
    }

    @Test
    fun 재고를_증가시킨다() {
        val result = controller.increaseStock(1L, 5)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        verify(inventoryCommandService).increaseStock(1L, 5)
    }

    @Test
    fun 재고를_예약한다() {
        whenever(inventoryCommandService.reserveStock(1L, 3)).thenReturn("rsv-123")

        val result = controller.reserveStock(1L, 3)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.reservationId).isEqualTo("rsv-123")
    }

    @Test
    fun 예약_확정_성공_시_true를_반환한다() {
        val ids = listOf("rsv-1", "rsv-2")
        whenever(inventoryCommandService.confirmReservations(ids)).thenReturn(true)

        val result = controller.confirmReservations(ids)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.confirmed).isTrue()
    }

    @Test
    fun 예약_확정_실패_시_false를_반환한다() {
        val ids = listOf("rsv-1", "rsv-2")
        whenever(inventoryCommandService.confirmReservations(ids)).thenThrow(ReservationConfirmFailedException())

        val result = controller.confirmReservations(ids)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.confirmed).isFalse()
    }

    @Test
    fun 단건_예약을_취소한다() {
        val result = controller.cancelReservation("rsv-1")

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        verify(inventoryCommandService).cancelReservation("rsv-1")
    }

    @Test
    fun 배치_예약을_취소한다() {
        val ids = listOf("rsv-1", "rsv-2")

        val result = controller.cancelReservations(ids)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        verify(inventoryCommandService).cancelReservations(ids)
    }

    @Test
    fun 배치_재고_예약을_수행한다() {
        val items = listOf(
            InternalInventoryController.ReserveRequest(1L, 3),
            InternalInventoryController.ReserveRequest(2L, 5)
        )
        val expected = mapOf(1L to "rsv-1", 2L to "rsv-2")
        whenever(inventoryCommandService.batchReserveStock(listOf(1L to 3, 2L to 5))).thenReturn(expected)

        val result = controller.batchReserveStock(items)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body).isEqualTo(expected)
    }
}
