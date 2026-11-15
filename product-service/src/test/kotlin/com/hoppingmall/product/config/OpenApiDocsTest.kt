package com.hoppingmall.product.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.product.support.TestCacheConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("API 명세와 실제 응답 모양 비교")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class OpenApiDocsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun 명세의_성공_응답_필드_이름이_실제_응답_JSON과_같다() {
        val docs = apiDocs()
        val actual = createCategory("{\"name\":\"명세확인\"}")

        val envelope = schema(docs, responseSchemaRef(docs, "/api/v1/categories", "post", "200"))
        val data = schema(docs, envelope.path("properties").path("data").path("\$ref").asText())

        assertDocumented(envelope, actual)
        assertThat(propertyNames(data)).containsExactlyInAnyOrderElementsOf(fieldNames(actual.path("data")))
    }

    @Test
    fun 명세의_요청_필드_이름으로_보낸_값이_실제로_반영된다() {
        val docs = apiDocs()
        val requestSchema = schema(docs, requestSchemaRef(docs, "/api/v1/categories", "post"))
        val parentField = propertyNames(requestSchema).single { it.startsWith("parent") }
        val parentId = createCategory("{\"name\":\"상위\"}").path("data").path("id").asLong()

        val child = createCategory("{\"name\":\"하위\",\"$parentField\":$parentId}")

        assertThat(child.path("data").path(parentField).asLong()).isEqualTo(parentId)
    }

    @Test
    fun 명세의_오류_응답이_실제_오류_봉투와_같다() {
        val docs = apiDocs()
        val response = mockMvc.get("/api/v1/products/999999999") {
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andReturn().response
        val actual = objectMapper.readTree(response.contentAsString)

        val operation = docs.path("paths").path("/api/v1/products/{productId}").path("get")
        val errorContent = operation.path("responses").path("default").path("content")
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
    fun 명세의_페이지_요청_인자를_쿼리_이름별로_보여준다() {
        val docs = apiDocs()

        val parameters = docs.path("paths").path("/api/v1/products/search").path("get").path("parameters")
            .map { it.path("name").asText() }

        assertThat(parameters).contains("page", "size", "sort", "keyword").doesNotContain("pageable", "condition")
    }

    @Test
    fun 멱등성_처리_입구는_명세에_멱등성_키_헤더를_필수로_보여준다() {
        val docs = apiDocs()

        val headers = docs.path("paths").path("/api/v1/wishlists").path("post").path("parameters")
            .filter { it.path("in").asText() == "header" }

        assertThat(headers).hasSize(1)
        assertThat(headers.single().path("name").asText()).isEqualTo("Idempotency-Key")
        assertThat(headers.single().path("required").asBoolean()).isTrue()
    }

    @Test
    fun 로그인_사용자_정보는_명세의_요청_인자로_드러나지_않는다() {
        val docs = apiDocs()

        val parameters = docs.path("paths").path("/api/v1/wishlists").path("get").path("parameters")
            .map { it.path("name").asText() }

        assertThat(parameters).containsExactlyInAnyOrder("page", "size", "sort")
    }

    private fun apiDocs(): JsonNode =
        objectMapper.readTree(mockMvc.get("/v3/api-docs").andReturn().response.contentAsString)

    private fun createCategory(body: String): JsonNode {
        val response = mockMvc.post("/api/v1/categories") {
            header("X-User-Id", "1")
            header("x-user-role", "ADMIN")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response
        assertThat(response.status).isEqualTo(201)
        return objectMapper.readTree(response.contentAsString)
    }

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
