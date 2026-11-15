package com.hoppingmall.order.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.OrderServiceApplication
import com.hoppingmall.order.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.MediaType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@DisplayName("API 명세와 실제 응답 모양 비교")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OpenApiDocsTest {

    @Test
    fun 명세의_성공_응답_필드_이름이_실제_응답_JSON과_같다() {
        val docs = apiDocs()
        val response = send(get("/api/v1/orders").buyer())
        val actual = objectMapper.readTree(response.body())

        val envelope = schema(docs, responseSchemaRef(docs, "/api/v1/orders", "get", "200"))
        val data = schema(docs, envelope.path("properties").path("data").path("\$ref").asText())
        val pageable = schema(docs, data.path("properties").path("pageable").path("\$ref").asText())

        assertThat(response.statusCode()).isEqualTo(200)
        assertDocumented(envelope, actual)
        assertThat(propertyNames(data)).containsExactlyInAnyOrderElementsOf(fieldNames(actual.path("data")))
        assertThat(propertyNames(pageable)).containsExactlyInAnyOrderElementsOf(fieldNames(actual.path("data").path("pageable")))
    }

    @Test
    fun 명세의_요청_필드_이름으로_보낸_본문을_읽는다() {
        val docs = apiDocs()
        val requestSchema = schema(docs, requestSchemaRef(docs, "/api/v1/orders", "post"))
        val addressSchema = schema(docs, requestSchema.path("properties").path("shipping_address").path("\$ref").asText())

        val expectedPriceSchema = schema(docs, requestSchema.path("properties").path("expected_prices").path("items").path("\$ref").asText())

        assertThat(propertyNames(requestSchema)).containsExactlyInAnyOrder("cart_item_ids", "shipping_address", "expected_prices")
        assertThat(requestSchema.path("required").map { it.asText() }).containsExactlyInAnyOrder("cart_item_ids", "shipping_address", "expected_prices")
        assertThat(propertyNames(expectedPriceSchema)).containsExactlyInAnyOrder("cart_item_id", "product_price")
        assertThat(propertyNames(addressSchema)).containsExactlyInAnyOrderElementsOf(SHIPPING_ADDRESS_VALUES.keys)
        assertThat(addressSchema.path("required").map { it.asText() })
            .containsExactlyInAnyOrder("recipient_name", "recipient_phone", "zip_code", "address")

        val address = objectMapper.createObjectNode()
        propertyNames(addressSchema).forEach { address.put(it, SHIPPING_ADDRESS_VALUES.getValue(it)) }
        val body = objectMapper.createObjectNode()
        body.putArray("cart_item_ids").add(999999999L)
        body.set<JsonNode>("shipping_address", address)
        body.putArray("expected_prices").addObject().put("cart_item_id", 999999999L).put("product_price", 10000)

        val response = send(
            request("/api/v1/orders").buyer()
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        )

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(objectMapper.readTree(response.body()).path("code").asText()).isEqualTo("ORD004")
    }

    @Test
    fun 멱등성_처리_입구는_명세에_멱등성_키_헤더를_필수로_보여준다() {
        val docs = apiDocs()

        val headers = docs.path("paths").path("/api/v1/orders").path("post").path("parameters")
            .filter { it.path("in").asText() == "header" }

        assertThat(headers).hasSize(1)
        assertThat(headers.single().path("name").asText()).isEqualTo("Idempotency-Key")
        assertThat(headers.single().path("required").asBoolean()).isTrue()
    }

    @Test
    fun 명세의_오류_응답이_실제_오류_봉투와_같다() {
        val docs = apiDocs()
        val response = send(get("/api/v1/orders/999999999").buyer())
        val actual = objectMapper.readTree(response.body())

        val errorContent = docs.path("paths").path("/api/v1/orders/{orderId}").path("get").path("responses").path("default").path("content")
        val errorSchema = schema(docs, errorContent.path(MediaType.APPLICATION_JSON_VALUE).path("schema").path("\$ref").asText())

        assertThat(response.statusCode()).isEqualTo(404)
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith(MediaType.APPLICATION_JSON_VALUE)
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
    fun 생성_입구의_명세_성공_응답은_실제와_같은_201이다() {
        val docs = apiDocs()
        listOf(
            "/api/v1/cart-items" to "post",
            "/api/v1/orders" to "post",
            "/api/v1/shipping" to "post"
        ).forEach { (path, method) ->
            val responses = docs.path("paths").path(path).path(method).path("responses")
            assertThat(responses.has("201")).describedAs("%s %s 201", method, path).isTrue()
            assertThat(responses.has("200")).describedAs("%s %s 200", method, path).isFalse()
        }
    }

    private fun apiDocs(): JsonNode = objectMapper.readTree(send(get("/v3/api-docs")).body())

    private fun request(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))

    private fun get(path: String): HttpRequest.Builder = request(path).GET()

    private fun HttpRequest.Builder.buyer(): HttpRequest.Builder =
        header("X-User-Id", "1").header("x-user-role", "BUYER")

    private fun send(builder: HttpRequest.Builder): HttpResponse<String> =
        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())

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

    companion object {
        private val objectMapper = ObjectMapper()
        private val httpClient: HttpClient = HttpClient.newHttpClient()
        private lateinit var context: ConfigurableApplicationContext
        private var port: Int = 0
        private val SHIPPING_ADDRESS_VALUES = mapOf(
            "recipient_name" to "홍길동",
            "recipient_phone" to "010-1234-5678",
            "zip_code" to "06236",
            "address" to "서울특별시 강남구 테헤란로 1",
            "address_detail" to "101호",
            "delivery_memo" to "문 앞에 놓아 주세요"
        )

        @JvmStatic
        @BeforeAll
        fun startApplication() {
            context = SpringApplicationBuilder(OrderServiceApplication::class.java)
                .properties(
                    mapOf(
                        "server.port" to "0",
                        "spring.autoconfigure.exclude" to "org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                        "spring.kafka.consumer.bootstrap-servers" to "localhost:1",
                        "spring.kafka.producer.bootstrap-servers" to "localhost:1",
                        "internal.service.token" to "api-docs-test-token",
                        "spring.datasource.url" to MySqlContainerSupport.jdbcUrl("order_api_docs_test"),
                        "spring.datasource.username" to MySqlContainerSupport.username,
                        "spring.datasource.password" to MySqlContainerSupport.password,
                        "spring.datasource.driver-class-name" to "com.mysql.cj.jdbc.Driver",
                        "spring.jpa.database-platform" to "org.hibernate.dialect.MySQLDialect"
                    )
                )
                .run()
            port = context.environment.getRequiredProperty("local.server.port", Int::class.java)
        }

        @JvmStatic
        @AfterAll
        fun stopApplication() {
            context.close()
        }
    }
}
