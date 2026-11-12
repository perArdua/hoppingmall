package com.hoppingmall.order.grpc

import com.hoppingmall.order.order.service.ReservationFence
import com.hoppingmall.reservation.grpc.ReservationLinkRequest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("GrpcReservationLinkService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class GrpcReservationLinkServiceTest {

    @Mock
    private lateinit var fence: ReservationFence

    @InjectMocks
    private lateinit var service: GrpcReservationLinkService

    @Test
    fun 예약마다_결정을_받아_LINKED와_ABORTED로_나눠_돌려준다() {
        whenever(fence.decide("rsv-1")).thenReturn(ReservationFence.LINKED)
        whenever(fence.decide("rsv-2")).thenReturn(ReservationFence.ABORTED)
        whenever(fence.decide("rsv-3")).thenReturn(ReservationFence.LINKED)
        val request = ReservationLinkRequest.newBuilder().addAllReservationIds(listOf("rsv-1", "rsv-2", "rsv-3")).build()

        val response = runBlocking { service.checkReservationLinks(request) }

        assertThat(response.linkedList).containsExactly("rsv-1", "rsv-3")
        assertThat(response.abortedList).containsExactly("rsv-2")
    }

    @Test
    fun 같은_예약이_요청에_여러_번_있어도_결정은_한_번만_받는다() {
        whenever(fence.decide("rsv-1")).thenReturn(ReservationFence.ABORTED)
        val request = ReservationLinkRequest.newBuilder().addAllReservationIds(listOf("rsv-1", "rsv-1")).build()

        val response = runBlocking { service.checkReservationLinks(request) }

        assertThat(response.linkedList).isEmpty()
        assertThat(response.abortedList).containsExactly("rsv-1")
        verify(fence, times(1)).decide("rsv-1")
    }

    @Test
    fun 빈_요청이면_결정을_받지_않고_빈_응답을_돌려준다() {
        val response = runBlocking { service.checkReservationLinks(ReservationLinkRequest.getDefaultInstance()) }

        assertThat(response.linkedList).isEmpty()
        assertThat(response.abortedList).isEmpty()
        verify(fence, never()).decide(any())
    }
}
