package com.hoppingmall.order.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

@DisplayName("InternalRestTemplateConfig")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InternalRestTemplateConfigTest {

    private val token = "test-internal-token"
    private val config = InternalRestTemplateConfig(token)

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    private fun buildRestTemplate(): Pair<RestTemplate, MockRestServiceServer> {
        val restTemplate = RestTemplate()
        config.internalTokenRestTemplateCustomizer().customize(restTemplate)
        val server = MockRestServiceServer.createServer(restTemplate)
        return restTemplate to server
    }

    @Test
    fun MDC에_traceId가_있으면_X_Trace_Id_헤더를_전파한다() {
        MDC.put("traceId", "trace-abc")
        val (restTemplate, server) = buildRestTemplate()
        server.expect(requestTo("http://external/api"))
            .andExpect(header("X-Trace-Id", "trace-abc"))
            .andRespond(withSuccess("", MediaType.TEXT_PLAIN))

        restTemplate.getForObject("http://external/api", String::class.java)

        server.verify()
    }

    @Test
    fun MDC에_userId가_있으면_X_User_Id_헤더를_전파한다() {
        MDC.put("userId", "user-42")
        val (restTemplate, server) = buildRestTemplate()
        server.expect(requestTo("http://external/api"))
            .andExpect(header("X-User-Id", "user-42"))
            .andRespond(withSuccess("", MediaType.TEXT_PLAIN))

        restTemplate.getForObject("http://external/api", String::class.java)

        server.verify()
    }

    @Test
    fun MDC가_비어있으면_trace_헤더를_설정하지_않는다() {
        val (restTemplate, server) = buildRestTemplate()
        server.expect(requestTo("http://external/api"))
            .andExpect(headerDoesNotExist("X-Trace-Id"))
            .andExpect(headerDoesNotExist("X-User-Id"))
            .andRespond(withSuccess("", MediaType.TEXT_PLAIN))

        restTemplate.getForObject("http://external/api", String::class.java)

        server.verify()
    }

    @Test
    fun internal_경로에서_Internal_Token과_trace_헤더가_함께_전파된다() {
        MDC.put("traceId", "trace-xyz")
        MDC.put("userId", "user-99")
        val (restTemplate, server) = buildRestTemplate()
        server.expect(requestTo("http://internal-service/internal/orders"))
            .andExpect(header("X-Trace-Id", "trace-xyz"))
            .andExpect(header("X-User-Id", "user-99"))
            .andExpect(header("X-Internal-Token", token))
            .andRespond(withSuccess("", MediaType.TEXT_PLAIN))

        restTemplate.getForObject("http://internal-service/internal/orders", String::class.java)

        server.verify()
    }

    @Test
    fun external_경로에서_trace_헤더만_전파되고_Internal_Token은_전파되지_않는다() {
        MDC.put("traceId", "trace-ext")
        val (restTemplate, server) = buildRestTemplate()
        server.expect(requestTo("http://external/api/orders"))
            .andExpect(header("X-Trace-Id", "trace-ext"))
            .andExpect(headerDoesNotExist("X-Internal-Token"))
            .andRespond(withSuccess("", MediaType.TEXT_PLAIN))

        restTemplate.getForObject("http://external/api/orders", String::class.java)

        server.verify()
    }
}
