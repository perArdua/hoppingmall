package com.hoppingmall.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("내부 토큰 필터의 거절 응답")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InternalTokenFilterResponseTest {

    private val filter = InternalTokenFilter(SERVICE_TOKEN)

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["wrong-token", ""])
    fun 내부_토큰이_없거나_틀리면_401과_인증_필요_오류_봉투다(token: String?) {
        val request = MockHttpServletRequest("GET", "/internal/orders/1")
        token?.let { request.addHeader("X-Internal-Token", it) }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(401)
        assertThat(response.contentType).startsWith("application/json")
        assertThat(ObjectMapper().readValue(response.getContentAsString(Charsets.UTF_8), Map::class.java))
            .isEqualTo(mapOf("code" to "A001", "message" to "인증이 필요합니다.", "data" to null))
        assertThat(chain.request).isNull()
    }

    @Test
    fun 내부_토큰이_맞으면_다음_필터로_넘긴다() {
        val request = MockHttpServletRequest("GET", "/internal/orders/1")
        request.addHeader("X-Internal-Token", SERVICE_TOKEN)
        val chain = MockFilterChain()

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(chain.request).isSameAs(request)
    }

    private companion object {
        const val SERVICE_TOKEN = "service-token"
    }
}
