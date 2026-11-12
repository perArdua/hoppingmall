package com.hoppingmall.product.grpc

import com.hoppingmall.reservation.grpc.ReservationLinkRequest
import com.hoppingmall.reservation.grpc.ReservationLinkResponse
import com.hoppingmall.reservation.grpc.ReservationLinkServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("GrpcReservationLinkAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class GrpcReservationLinkAdapterTest {

    private val service = FakeLinkService()
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    @BeforeEach
    fun setUp() {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name).addService(service).build().start()
        channel = InProcessChannelBuilder.forName(name).build()
    }

    @AfterEach
    fun tearDown() {
        service.release.countDown()
        channel.shutdownNow()
        server.shutdownNow()
        assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun 응답의_linked와_aborted를_그대로_옮긴다() {
        service.answer = { request ->
            ReservationLinkResponse.newBuilder()
                .addAllLinked(request.reservationIdsList.filter { it.startsWith("linked") })
                .addAllAborted(request.reservationIdsList.filter { it.startsWith("aborted") })
                .build()
        }

        val result = adapter(deadlineMs = 5_000).check(listOf("linked-1", "aborted-1", "linked-2"))

        assertThat(service.requests).containsExactly(listOf("linked-1", "aborted-1", "linked-2"))
        assertThat(result.linked).containsExactly("linked-1", "linked-2")
        assertThat(result.aborted).containsExactly("aborted-1")
    }

    @Test
    fun 서버가_UNAVAILABLE이면_예외가_그대로_나온다() {
        service.failure = Status.UNAVAILABLE.withDescription("연결 조회 불가")

        assertThatThrownBy { adapter(deadlineMs = 5_000).check(listOf("rsv-1")) }
            .isInstanceOfSatisfying(StatusRuntimeException::class.java) {
                assertThat(it.status.code).isEqualTo(Status.Code.UNAVAILABLE)
            }
    }

    @Test
    fun 서버가_기한_안에_응답하지_않으면_DEADLINE_EXCEEDED_예외가_나온다() {
        service.holdResponse = true

        assertThatThrownBy { adapter(deadlineMs = 200).check(listOf("rsv-1")) }
            .isInstanceOfSatisfying(StatusRuntimeException::class.java) {
                assertThat(it.status.code).isEqualTo(Status.Code.DEADLINE_EXCEEDED)
            }
        assertThat(service.requests).containsExactly(listOf("rsv-1"))
    }

    private fun adapter(deadlineMs: Long) =
        GrpcReservationLinkAdapter(ReservationLinkServiceGrpc.newBlockingStub(channel), deadlineMs)

    private class FakeLinkService : ReservationLinkServiceGrpc.ReservationLinkServiceImplBase() {
        val requests = CopyOnWriteArrayList<List<String>>()
        val release = CountDownLatch(1)

        @Volatile
        var answer: (ReservationLinkRequest) -> ReservationLinkResponse = { ReservationLinkResponse.getDefaultInstance() }

        @Volatile
        var failure: Status? = null

        @Volatile
        var holdResponse: Boolean = false

        override fun checkReservationLinks(request: ReservationLinkRequest, observer: StreamObserver<ReservationLinkResponse>) {
            requests.add(request.reservationIdsList.toList())
            failure?.let {
                observer.onError(it.asRuntimeException())
                return
            }
            if (holdResponse) release.await()
            observer.onNext(answer(request))
            observer.onCompleted()
        }
    }
}
