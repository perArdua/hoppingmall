package com.hoppingmall.gateway

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = [
    "jwt.secret=test-secret-key-for-testing-purposes-only-32chars!",
    "spring.cloud.gateway.routes[0].id=test",
    "spring.cloud.gateway.routes[0].uri=http://localhost:8080",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/test/**"
])
class ApiGatewayApplicationTests {
    @Test
    fun contextLoads() {
    }
}
