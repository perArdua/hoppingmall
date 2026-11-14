package com.hoppingmall.payment.grpc

import io.grpc.Channel
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthGrpc
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.protobuf.services.HealthStatusManager
import net.devh.boot.grpc.client.inject.GrpcClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@DisplayName("ConnectBackoffResetClientInterceptor")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ConnectBackoffResetClientInterceptorTest {

    private val serverName = InProcessServerBuilder.generateName()
    private val managedChannel: ManagedChannel = InProcessChannelBuilder.forName(serverName).build()
    private var server: Server? = null

    @AfterEach
    fun tearDown() {
        managedChannel.shutdownNow()
        server?.shutdownNow()
    }

    private fun startServer() {
        server = InProcessServerBuilder.forName(serverName)
            .addService(HealthStatusManager().healthService)
            .build()
            .start()
    }

    private fun check(channel: Channel): Status.Code = try {
        HealthGrpc.newBlockingStub(channel)
            .withDeadlineAfter(5, TimeUnit.SECONDS)
            .check(HealthCheckRequest.getDefaultInstance())
        Status.Code.OK
    } catch (e: StatusRuntimeException) {
        e.status.code
    }

    @Test
    fun 상대가_다시_뜬_뒤_UNAVAILABLE_한_번이면_다음_호출은_재연결_대기를_기다리지_않고_성공한다() {
        val channel = ClientInterceptors.interceptForward(
            managedChannel,
            DeadlineClientInterceptor(),
            ConnectBackoffResetClientInterceptor()
        )
        assertThat(check(channel)).isEqualTo(Status.Code.UNAVAILABLE)

        startServer()
        check(channel)

        assertThat(check(channel)).isEqualTo(Status.Code.OK)
    }

    @Test
    fun 모든_gRPC_클라이언트_주입에_재연결_대기_초기화_인터셉터가_있다() {
        val clients = listOf(GrpcMembershipQueryAdapter::class, GrpcOrderQueryAdapter::class)
            .flatMap { adapter -> adapter.java.declaredConstructors.flatMap { it.parameters.toList() } }
            .mapNotNull { it.getAnnotation(GrpcClient::class.java) }

        assertThat(clients).hasSize(2)
        assertThat(clients).allSatisfy { client ->
            assertThat(client.interceptors.toList()).contains(ConnectBackoffResetClientInterceptor::class)
        }
    }
}
