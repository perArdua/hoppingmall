package com.hoppingmall.order

import com.hoppingmall.order.grpc.GrpcOrderQueryService
import com.hoppingmall.order.support.MySqlContainerSupport
import net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@DisplayName("gRPC 서버·보안 자동 구성을 포함한 order 전체 컨텍스트 기동")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "grpc.server.port=0",
        "spring.kafka.consumer.bootstrap-servers=localhost:1",
        "spring.kafka.producer.bootstrap-servers=localhost:1",
        "internal.service.token=startup-test-token"
    ]
)
@ActiveProfiles("grpc")
class OrderServiceStartupTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun grpc_프로필에서_gRPC_서버와_보안_설정을_포함한_컨텍스트가_뜬다() {
        assertThat(context.getBeansOfType(SecurityFilterChain::class.java)).isNotEmpty()
        assertThat(context.getBean(GrpcOrderQueryService::class.java)).isNotNull()
        val grpcServers = context.getBeansOfType(GrpcServerLifecycle::class.java).values.map { it.isRunning }
        assertThat(grpcServers).containsExactly(true)
    }

    @Test
    fun 기본_보안_사용자_계정을_만들지_않는다() {
        assertThat(context.getBeanNamesForType(UserDetailsService::class.java)).isEmpty()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "order_startup_test")
        }
    }
}
