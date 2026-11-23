package com.hoppingmall.order.order.service

import com.hoppingmall.order.grpc.GrpcReservationLinkService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.io.ClassPathResource

@DisplayName("배포 프로필의 예약 fence 스위치")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReservationFenceDeployProfileTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(ReservationFence::class.java, { mock<ReservationFence>() })
        .withUserConfiguration(GrpcReservationLinkService::class.java)

    @Test
    fun docker와_k8s_프로필은_fence를_켠다() {
        assertThat(fenceSwitch("application-docker.yml")).isEqualTo("true")
        assertThat(fenceSwitch("application-k8s.yml")).isEqualTo("true")
    }

    @Test
    fun 배포_프로필_값이면_product의_링크_확인에_답하는_gRPC_서비스가_등록된다() {
        listOf("application-docker.yml", "application-k8s.yml").forEach { resource ->
            contextRunner.withPropertyValues("$FENCE_ENABLED=${fenceSwitch(resource)}").run { context ->
                assertThat(context).hasSingleBean(GrpcReservationLinkService::class.java)
            }
        }
    }

    private fun fenceSwitch(resource: String): String? =
        YamlPropertySourceLoader().load(resource, ClassPathResource(resource))
            .firstNotNullOfOrNull { it.getProperty(FENCE_ENABLED) }
            ?.toString()

    companion object {
        private const val FENCE_ENABLED = "order.reservation.fence.enabled"
    }
}
