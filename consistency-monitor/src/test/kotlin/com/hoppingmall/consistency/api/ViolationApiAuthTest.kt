package com.hoppingmall.consistency.api

import com.hoppingmall.consistency.auth.TestTokens
import com.hoppingmall.consistency.violation.Violation
import com.hoppingmall.consistency.violation.ViolationRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(ViolationController::class, properties = ["jwt.secret=${TestTokens.SECRET}"])
@DisplayName("위반 API 인증")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ViolationApiAuthTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var repository: ViolationRepository

    private val t0 = LocalDateTime.of(2026, 10, 22, 19, 0, 0)

    private fun violation(resolvedAt: LocalDateTime? = null, ackedBy: String? = null) =
        Violation(5L, "INV-1", "10", "CRITICAL", t0, t0, t0, resolvedAt, ackedBy, null, "{}")

    private fun resolveWith(authorization: String?) = mockMvc.post("/api/v1/violations/5/resolve") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"ackedBy":"kim","resolutionNote":"환불 확인"}"""
        authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
    }

    @Test
    fun 토큰_없이_위반_목록을_조회하면_401() {
        mockMvc.get("/api/v1/violations").andExpect { status { isUnauthorized() } }
        verifyNoInteractions(repository)
    }

    @Test
    fun 토큰_없이_위반을_닫으면_401이고_닫히지_않는다() {
        resolveWith(null).andExpect { status { isUnauthorized() } }
        verify(repository, never()).resolve(any(), any(), any(), any())
    }

    @ParameterizedTest
    @ValueSource(strings = ["BUYER", "SELLER"])
    fun ADMIN이_아닌_역할은_403(role: String) {
        resolveWith(TestTokens.bearer(TestTokens.token(3L, role))).andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/violations") {
            header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.token(3L, role)))
        }.andExpect { status { isForbidden() } }
        verifyNoInteractions(repository)
    }

    @Test
    fun 역할이_없는_토큰은_게이트웨이와_같이_401() {
        resolveWith(TestTokens.bearer(TestTokens.token(3L, null))).andExpect { status { isUnauthorized() } }
        verifyNoInteractions(repository)
    }

    @Test
    fun 다른_키로_서명한_토큰은_401() {
        resolveWith(TestTokens.bearer(TestTokens.signedWithOtherKey())).andExpect { status { isUnauthorized() } }
        verifyNoInteractions(repository)
    }

    @Test
    fun 만료된_토큰은_401() {
        resolveWith(TestTokens.bearer(TestTokens.token(7L, "ADMIN", expiresInMs = -60_000)))
            .andExpect { status { isUnauthorized() } }
        verifyNoInteractions(repository)
    }

    @Test
    fun 리프레시_토큰은_401() {
        resolveWith(TestTokens.bearer(TestTokens.token(7L, "ADMIN", type = "refresh")))
            .andExpect { status { isUnauthorized() } }
        verifyNoInteractions(repository)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "Basic abc", "Bearer ", "Bearer not-a-jwt"])
    fun Bearer_토큰이_아니면_401(authorization: String) {
        resolveWith(authorization).andExpect { status { isUnauthorized() } }
        verifyNoInteractions(repository)
    }

    @Test
    fun 위반을_닫은_사람은_본문이_아니라_토큰의_관리자로_기록한다() {
        whenever(repository.findById(5L)).thenReturn(violation(), violation(t0.plusMinutes(1), "7"))
        whenever(repository.now()).thenReturn(t0.plusMinutes(1))

        resolveWith(TestTokens.bearer(TestTokens.admin(7L))).andExpect {
            status { isOk() }
            jsonPath("$.ackedBy") { value("7") }
        }
        verify(repository).resolve(5L, t0.plusMinutes(1), "7", "환불 확인")
    }

    @Test
    fun typ가_없는_ADMIN_토큰은_게이트웨이와_같이_받는다() {
        whenever(repository.findAll(true)).thenReturn(listOf(violation()))

        mockMvc.get("/api/v1/violations") {
            header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.token(7L, "ADMIN", type = null)))
        }.andExpect { status { isOk() } }
    }
}
