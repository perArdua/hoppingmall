package com.hoppingmall.gateway.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.yaml.snakeyaml.Yaml

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["jwt.secret=test-secret-key-for-testing-purposes-only-32chars!"]
)
@DisplayName("게이트웨이 CORS 허용 헤더")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CorsAllowedHeadersTest {

    companion object {
        private const val FRONT_ORIGIN = "http://localhost:3000"
        private val ALLOWED_HEADERS = listOf("Authorization", "Content-Type", "Idempotency-Key", "X-Token-Transport")
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    private fun preflight(requestHeaders: String): WebTestClient.ResponseSpec =
        webTestClient.method(HttpMethod.OPTIONS).uri("/api/v1/orders")
            .header("Origin", FRONT_ORIGIN)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", requestHeaders)
            .exchange()

    @Test
    fun 프론트가_보내는_헤더는_사전_요청을_통과한다() {
        preflight("authorization, content-type, idempotency-key, x-token-transport")
            .expectStatus().isOk
            .expectHeader().valueEquals("Access-Control-Allow-Origin", FRONT_ORIGIN)
            .expectHeader().value("Access-Control-Allow-Headers") { allowed ->
                assertThat(allowed.split(",").map { it.trim().lowercase() })
                    .containsExactlyInAnyOrder("authorization", "content-type", "idempotency-key", "x-token-transport")
            }
    }

    @Test
    fun 목록에_없는_헤더만_보내려는_사전_요청은_거절한다() {
        preflight("x-user-id")
            .expectStatus().isForbidden
            .expectHeader().doesNotExist("Access-Control-Allow-Origin")
    }

    @Test
    fun 목록에_없는_헤더는_허용_헤더_응답에서_빠져_브라우저가_본_요청을_막는다() {
        preflight("authorization, x-user-id")
            .expectHeader().value("Access-Control-Allow-Headers") { allowed ->
                assertThat(allowed.split(",").map { it.trim().lowercase() }).containsExactly("authorization")
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml", "application-k8s.yml"])
    fun 모든_프로필이_같은_허용_헤더_목록을_쓴다(file: String) {
        assertThat(allowedHeadersIn(file)).isEqualTo(ALLOWED_HEADERS)
    }

    @Suppress("UNCHECKED_CAST")
    private fun allowedHeadersIn(file: String): List<String> {
        val root = ClassPathResource(file).inputStream.use { Yaml().loadAll(it).first() as Map<String, Any?> }
        val gateway = ((root["spring"] as Map<String, Any?>)["cloud"] as Map<String, Any?>)["gateway"] as Map<String, Any?>
        val cors = (gateway["globalcors"] as Map<String, Any?>)["cors-configurations"] as Map<String, Any?>
        return (cors["[/**]"] as Map<String, Any?>)["allowedHeaders"] as List<String>
    }
}
