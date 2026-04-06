package com.hoppingmall.product

import com.hoppingmall.product.support.TestCacheConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.context.annotation.Import

@SpringBootTest
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class ProductServiceApplicationTests {
    @Test
    fun contextLoads() {
    }
}
