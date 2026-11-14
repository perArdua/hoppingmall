package com.hoppingmall.consistency.auth

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:monitor-auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.connection-init-sql=SELECT 1",
        "consistency.initial-delay-ms=3600000",
        "jwt.secret=${TestTokens.SECRET}",
        "management.prometheus.metrics.export.enabled=true"
    ]
)
@AutoConfigureMockMvc
@DisplayName("consistency-monitor 인증 범위")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AdminTokenFilterContextTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun 헬스체크와_프로메테우스_수집은_토큰_없이_열려_있다() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
        mockMvc.get("/actuator/prometheus").andExpect { status { isOk() } }
    }

    @Test
    fun 위반_API는_토큰이_없으면_401() {
        mockMvc.get("/api/v1/violations").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 관리자_토큰이면_실제_저장소로_위반_목록을_돌려준다() {
        mockMvc.get("/api/v1/violations") {
            header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.admin()))
        }.andExpect { status { isOk() } }
    }

    @Test
    fun 액추에이터_밖의_모르는_경로도_토큰이_없으면_401() {
        mockMvc.get("/api/v1/unknown").andExpect { status { isUnauthorized() } }
        mockMvc.get("/actuatorx").andExpect { status { isUnauthorized() } }
    }
}
