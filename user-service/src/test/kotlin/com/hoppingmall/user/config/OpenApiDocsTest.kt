package com.hoppingmall.user.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@DisplayName("API 명세와 실제 응답 모양 비교")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest
@AutoConfigureMockMvc
@Import(OpenApiDocsTest.TestInfraConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test"
])
class OpenApiDocsTest {

    @TestConfiguration
    class TestInfraConfig {

        @Bean
        @Primary
        fun redisConnectionFactory(): RedisConnectionFactory {
            return Mockito.mock(RedisConnectionFactory::class.java)
        }

        @Bean
        @Primary
        fun stringRedisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
            val template = RedisTemplate<String, String>()
            template.connectionFactory = redisConnectionFactory
            template.keySerializer = StringRedisSerializer()
            template.valueSerializer = StringRedisSerializer()
            return template
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun 명세의_성공_응답_필드_이름이_실제_응답_JSON과_같다() {
        val docs = apiDocs()
        val userId = signUp()

        val response = mockMvc.get("/api/v1/memberships/me") {
            header("X-User-Id", userId.toString())
            header("x-user-role", "BUYER")
        }.andReturn().response
        val actual = objectMapper.readTree(response.contentAsString)

        val envelope = schema(docs, responseSchemaRef(docs, "/api/v1/memberships/me", "get", "200"))
        val data = schema(docs, envelope.path("properties").path("data").path("\$ref").asText())

        assertThat(response.status).isEqualTo(200)
        assertDocumented(envelope, actual)
        assertThat(propertyNames(data)).containsExactlyInAnyOrderElementsOf(fieldNames(actual.path("data")))
    }

    @Test
    fun 명세의_요청_필드_이름으로_보내면_요청이_받아들여진다() {
        val docs = apiDocs()
        val requestSchema = schema(docs, requestSchemaRef(docs, "/api/v1/sellers/apply", "post"))
        val field = propertyNames(requestSchema).single()
        val userId = signUp()

        val response = mockMvc.post("/api/v1/sellers/apply") {
            header("X-User-Id", userId.toString())
            header("x-user-role", "BUYER")
            contentType = MediaType.APPLICATION_JSON
            content = "{\"$field\":\"123-45-67890\"}"
        }.andReturn().response

        assertThat(response.status).isEqualTo(200)
        assertThat(objectMapper.readTree(response.contentAsString).path("code").asText()).isEqualTo("SUCCESS")
    }

    @Test
    fun 명세의_오류_응답이_실제_오류_봉투와_같다() {
        val docs = apiDocs()
        val response = mockMvc.get("/api/v1/users/me") {
            header("X-User-Id", "999999")
            header("x-user-role", "BUYER")
        }.andReturn().response
        val actual = objectMapper.readTree(response.contentAsString)

        val errorContent = docs.path("paths").path("/api/v1/users/me").path("get").path("responses").path("default").path("content")
        val errorSchema = schema(docs, errorContent.path(MediaType.APPLICATION_JSON_VALUE).path("schema").path("\$ref").asText())

        assertThat(response.status).isEqualTo(404)
        assertThat(response.contentType).startsWith(MediaType.APPLICATION_JSON_VALUE)
        assertDocumented(errorSchema, actual)
        assertThat(actual.path("code").asText()).isNotBlank()
    }

    @Test
    fun 명세의_응답_미디어_타입이_JSON이다() {
        val docs = apiDocs()

        val mediaTypes = docs.path("paths").properties().flatMap { (_, pathItem) ->
            pathItem.properties().flatMap { (_, operation) ->
                operation.path("responses").properties().flatMap { (_, response) -> response.path("content").fieldNames().asSequence().toList() }
            }
        }

        assertThat(mediaTypes).isNotEmpty().doesNotContain("*/*")
    }

    @Test
    fun 로그인_사용자_정보는_명세의_요청_인자로_드러나지_않는다() {
        val docs = apiDocs()

        val parameters = docs.path("paths").path("/api/v1/memberships").path("post").path("parameters")

        assertThat(parameters.map { it.path("name").asText() }).isEmpty()
    }

    private fun signUp(): Long {
        val response = mockMvc.post("/api/v1/users/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\"email\":\"docs-${UUID.randomUUID()}@example.com\",\"password\":\"password123!\",\"name\":\"명세\"}"
        }.andReturn().response
        assertThat(response.status).isEqualTo(200)
        return objectMapper.readTree(response.contentAsString).path("data").path("id").asLong()
    }

    @Test
    fun 생성_입구의_명세_성공_응답은_실제와_같은_201이다() {
        val docs = apiDocs()
        listOf(
            "/api/v1/memberships" to "post"
        ).forEach { (path, method) ->
            val responses = docs.path("paths").path(path).path(method).path("responses")
            assertThat(responses.has("201")).describedAs("%s %s 201", method, path).isTrue()
            assertThat(responses.has("200")).describedAs("%s %s 200", method, path).isFalse()
        }
    }

    private fun apiDocs(): JsonNode =
        objectMapper.readTree(mockMvc.get("/v3/api-docs").andReturn().response.contentAsString)

    private fun responseSchemaRef(docs: JsonNode, path: String, method: String, status: String): String =
        docs.path("paths").path(path).path(method).path("responses").path(status)
            .path("content").path(MediaType.APPLICATION_JSON_VALUE).path("schema").path("\$ref").asText()

    private fun requestSchemaRef(docs: JsonNode, path: String, method: String): String =
        docs.path("paths").path(path).path(method).path("requestBody")
            .path("content").path(MediaType.APPLICATION_JSON_VALUE).path("schema").path("\$ref").asText()

    private fun schema(docs: JsonNode, ref: String): JsonNode =
        docs.path("components").path("schemas").path(ref.substringAfterLast('/'))

    private fun assertDocumented(schema: JsonNode, actual: JsonNode) {
        assertThat(propertyNames(schema)).containsAll(fieldNames(actual))
        assertThat(fieldNames(actual)).containsAll(schema.path("required").map { it.asText() })
    }

    private fun propertyNames(schema: JsonNode): List<String> = schema.path("properties").fieldNames().asSequence().toList()

    private fun fieldNames(node: JsonNode): List<String> = node.fieldNames().asSequence().toList()
}
