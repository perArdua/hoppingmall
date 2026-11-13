package com.hoppingmall.gateway.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.time.Duration

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=test-secret-key-for-testing-purposes-only-32chars!",
        "spring.data.redis.timeout=3s"
    ]
)
@DisplayName("운영자가 정한 Lettuce 명령 시간 제한")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LettuceCommandTimeoutOverrideTest {

    @Autowired
    private lateinit var connectionFactory: LettuceConnectionFactory

    @Test
    fun spring_data_redis_timeout을_주면_코드_기본값_대신_그_값을_따른다() {
        assertThat(connectionFactory.clientConfiguration.commandTimeout).isEqualTo(Duration.ofSeconds(3))
    }
}
