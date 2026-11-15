package com.hoppingmall.payment.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("API 명세와 실제 응답 모양 비교")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4",
        "spring.kafka.consumer.bootstrap-servers=localhost:1",
        "spring.kafka.producer.bootstrap-servers=localhost:1",
        "internal.service.token=api-docs-test-token"
    ]
)
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private lateinit var redissonClient: RedissonClient

    @Test
    fun 명세의_요청과_응답_필드_이름이_실제_JSON과_같다() {
        val docs = apiDocs()
        val requestFields = propertyNames(schema(docs, requestSchemaRef(docs, "/api/v1/admin/coupons", "post")))

        val response = mockMvc.post("/api/v1/admin/coupons") {
            header("X-User-Id", "1")
            header("x-user-role", "ADMIN")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(couponRequest(requestFields))
        }.andReturn().response
        val actual = objectMapper.readTree(response.contentAsString)

        val envelope = schema(docs, responseSchemaRef(docs, "/api/v1/admin/coupons", "post"))
        val data = schema(docs, envelope.path("properties").path("data").path("\$ref").asText())

        assertThat(response.status).isEqualTo(201)
        assertDocumented(envelope, actual)
        assertThat(propertyNames(data)).containsExactlyInAnyOrderElementsOf(fieldNames(actual.path("data")))
        assertThat(actual.path("data").path("min_order_amount").decimalValue()).isEqualByComparingTo("10000")
    }

    @Test
    fun 명세의_오류_응답이_실제_오류_봉투와_같다() {
        val docs = apiDocs()
        val response = mockMvc.get("/api/v1/payments/999999999") {
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andReturn().response
        val actual = objectMapper.readTree(response.contentAsString)

        val errorContent = docs.path("paths").path("/api/v1/payments/{paymentId}").path("get").path("responses").path("default").path("content")
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

        val parameters = docs.path("paths").path("/api/v1/points/my-history").path("get").path("parameters")
            .map { it.path("name").asText() }

        assertThat(parameters).containsExactlyInAnyOrder("page", "size")
    }

    private fun couponRequest(fields: List<String>): Map<String, Any> {
        val values = mapOf(
            "name" to "명세 확인 쿠폰",
            "code" to "DOCS-COUPON",
            "discount_type" to "FIXED_AMOUNT",
            "discount_value" to 1000,
            "min_order_amount" to 10000,
            "max_discount_amount" to 1000,
            "total_quantity" to 10,
            "valid_from" to "2026-01-01T00:00:00",
            "valid_to" to "2099-12-31T23:59:59"
        )
        assertThat(fields).containsExactlyInAnyOrderElementsOf(values.keys)
        return values
    }

    private fun apiDocs(): JsonNode =
        objectMapper.readTree(mockMvc.get("/v3/api-docs").andReturn().response.contentAsString)

    private fun responseSchemaRef(docs: JsonNode, path: String, method: String): String =
        docs.path("paths").path(path).path(method).path("responses").properties()
            .first { (status, _) -> status.startsWith("2") }.value
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

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "payment_api_docs_test")
        }
    }
}
