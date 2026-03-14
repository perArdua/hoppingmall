package com.hoppingmall.mall

import com.hoppingmall.mall.global.common.config.TestRedisConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("test")
@Import(TestRedisConfig::class)
class MallApplicationTests {

	@MockBean
	private lateinit var redisMessageListenerContainer: RedisMessageListenerContainer

	@Test
	fun contextLoads() {
	}

}
