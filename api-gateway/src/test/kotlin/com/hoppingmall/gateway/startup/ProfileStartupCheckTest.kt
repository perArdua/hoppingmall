package com.hoppingmall.gateway.startup

import com.hoppingmall.gateway.ApiGatewayApplication
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.ServerSocket
import java.util.UUID

@Tag("startup")
@DisplayName("운영 프로필 설정으로 애플리케이션 기동")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProfileStartupCheckTest {

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 운영_프로필_설정과_필수_환경_변수만으로_컨텍스트가_뜬다(profile: String) {
        SpringApplicationBuilder(ApiGatewayApplication::class.java)
            .environment(requiredEnvironment())
            .profiles(profile)
            .run(*infrastructureArguments())
            .use { context -> assertThat(context.isActive).isTrue() }
    }

    private fun requiredEnvironment(): StandardEnvironment = StandardEnvironment().apply {
        propertySources.addFirst(
            SystemEnvironmentPropertySource(
                "startupCheckEnvironment",
                mapOf(
                    "GATEWAY_IDENTITY_SECRET" to UUID.randomUUID().toString(),
                    "JWT_SECRET" to UUID.randomUUID().toString()
                )
            )
        )
    }

    private fun infrastructureArguments(): Array<String> = arrayOf(
        "--spring.data.redis.host=$LOOPBACK",
        "--spring.data.redis.port=$redisPort",
        "--spring.data.redis.cluster.nodes=$LOOPBACK:$redisPort",
        "--server.port=0"
    )

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val CLUSTER_BUS_PORT = 16379
        private val redisPort = ServerSocket(0).use { it.localPort }

        private val redis = GenericContainer("redis:7.2-alpine")
            .withExposedPorts(redisPort)
            .withCreateContainerCmdModifier { command ->
                command.hostConfig?.withPortBindings(PortBinding(Ports.Binding.bindPort(redisPort), ExposedPort(redisPort)))
            }
            .withCommand(
                "redis-server", "--port", "$redisPort", "--cluster-enabled", "yes",
                "--cluster-port", "$CLUSTER_BUS_PORT",
                "--cluster-announce-ip", LOOPBACK, "--cluster-announce-port", "$redisPort"
            )
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))

        @JvmStatic
        @BeforeAll
        fun startInfrastructure() {
            redis.start()
            redis.execInContainer("redis-cli", "-p", "$redisPort", "cluster", "addslotsrange", "0", "16383")
            repeat(60) {
                if (redis.execInContainer("redis-cli", "-p", "$redisPort", "cluster", "info").stdout.contains("cluster_state:ok")) {
                    return
                }
                Thread.sleep(500)
            }
            error("Redis 클러스터가 준비되지 않았습니다")
        }

        @JvmStatic
        @AfterAll
        fun stopInfrastructure() {
            redis.stop()
        }
    }
}
