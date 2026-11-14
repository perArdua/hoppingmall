package com.hoppingmall.common.grpc

import net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration
import net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration
import net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityHealthContributorAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityProbesAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.actuate.health.CompositeHealth
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan("com.hoppingmall.common.grpc")
class CommonGrpcScanConfig

@DisplayName("gRPC 서버 기동을 포함한 health와 준비 상태")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GrpcServerReadinessTest {

    private val healthRunner = WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ApplicationAvailabilityAutoConfiguration::class.java,
                HealthContributorAutoConfiguration::class.java,
                HealthEndpointAutoConfiguration::class.java,
                AvailabilityHealthContributorAutoConfiguration::class.java,
                AvailabilityProbesAutoConfiguration::class.java
            )
        )
        .withUserConfiguration(CommonGrpcScanConfig::class.java)

    private val grpcRunner = healthRunner
        .withConfiguration(
            AutoConfigurations.of(
                GrpcServerAutoConfiguration::class.java,
                GrpcServerFactoryAutoConfiguration::class.java
            )
        )
        .withPropertyValues(
            "grpc.server.port=-1",
            "grpc.server.in-process-name=readiness-test",
            "management.endpoint.health.probes.enabled=true",
            "management.endpoint.health.group.readiness.include=readinessState,grpcServer"
        )

    @Test
    fun gRPC_서버가_아직_시작되지_않았으면_health가_UP이_아니다() {
        healthRunner
            .withBean("grpcServerLifecycle", GrpcServerLifecycle::class.java, { mock<GrpcServerLifecycle>() })
            .run { context ->
                assertThat(context).hasNotFailed()
                val health = context.getBean(HealthEndpoint::class.java).health()

                assertThat(health.status).isEqualTo(Status.OUT_OF_SERVICE)
            }
    }

    @Test
    fun gRPC_서버가_뜨면_readiness_그룹에_gRPC_서버가_UP으로_들어간다() {
        grpcRunner.run { context ->
            assertThat(context).hasNotFailed()
            val readiness = context.getBean(HealthEndpoint::class.java).healthForPath("readiness") as CompositeHealth

            assertThat(readiness.components.keys).containsExactlyInAnyOrder("readinessState", "grpcServer")
            assertThat(readiness.components["grpcServer"]!!.status).isEqualTo(Status.UP)
        }
    }

    @Test
    fun gRPC_서버가_멈추면_health와_readiness_그룹의_gRPC_서버가_UP이_아니다() {
        grpcRunner.run { context ->
            assertThat(context).hasNotFailed()
            context.getBeansOfType(GrpcServerLifecycle::class.java).values.forEach { it.stop() }
            val endpoint = context.getBean(HealthEndpoint::class.java)
            val health = endpoint.health() as CompositeHealth
            val readiness = endpoint.healthForPath("readiness") as CompositeHealth

            assertThat(health.components["grpcServer"]!!.status).isEqualTo(Status.OUT_OF_SERVICE)
            assertThat(readiness.components["grpcServer"]!!.status).isEqualTo(Status.OUT_OF_SERVICE)
        }
    }

    @Test
    fun gRPC_서버_라이브러리가_없는_서비스에는_gRPC_서버_구성원이_없다() {
        healthRunner
            .withClassLoader(FilteredClassLoader(GrpcServerLifecycle::class.java))
            .run { context ->
                assertThat(context).hasNotFailed()
                val health = context.getBean(HealthEndpoint::class.java).health() as CompositeHealth

                assertThat(health.components).doesNotContainKey("grpcServer")
                assertThat(health.status).isEqualTo(Status.UP)
            }
    }
}
