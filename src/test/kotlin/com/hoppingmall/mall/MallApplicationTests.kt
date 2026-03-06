package com.hoppingmall.mall

import com.hoppingmall.mall.global.common.config.TestRedisConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig::class)
class MallApplicationTests {

	@Test
	fun contextLoads() {
	}

}
