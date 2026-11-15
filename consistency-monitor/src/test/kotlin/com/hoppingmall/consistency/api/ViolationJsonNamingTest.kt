package com.hoppingmall.consistency.api

import com.hoppingmall.consistency.auth.TestTokens
import com.hoppingmall.consistency.violation.Violation
import com.hoppingmall.consistency.violation.ViolationRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
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
@DisplayName("위반 API JSON 이름")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ViolationJsonNamingTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var repository: ViolationRepository

    private val t0 = LocalDateTime.of(2026, 11, 16, 9, 0, 0)

    private val adminAuthorization = TestTokens.bearer(TestTokens.admin(7L))

    private fun violation(resolvedAt: LocalDateTime? = null, note: String? = null) =
        Violation(5L, "INV-1", "10", "CRITICAL", t0, t0, t0, resolvedAt, null, note, "{}")

    @Test
    fun 위반_목록은_snake_case_이름으로_나간다() {
        whenever(repository.findAll(true)).thenReturn(listOf(violation()))

        mockMvc.get("/api/v1/violations") {
            header(HttpHeaders.AUTHORIZATION, adminAuthorization)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].inv_id") { value("INV-1") }
            jsonPath("$[0].entity_key") { value("10") }
            jsonPath("$[0].first_seen") { exists() }
            jsonPath("$[0].last_seen") { exists() }
            jsonPath("$[0].confirmed_at") { exists() }
            jsonPath("$[0].snapshot_json") { value("{}") }
            jsonPath("$[0].invId") { doesNotExist() }
        }
    }

    @Test
    fun 닫힌_위반도_보려면_open_only를_false로_보낸다() {
        whenever(repository.findAll(false)).thenReturn(emptyList())

        mockMvc.get("/api/v1/violations") {
            header(HttpHeaders.AUTHORIZATION, adminAuthorization)
            param("open_only", "false")
        }.andExpect { status { isOk() } }

        verify(repository).findAll(false)
    }

    @Test
    fun 해소_사유는_snake_case_이름으로_받고_돌려준다() {
        whenever(repository.findById(5L)).thenReturn(violation(), violation(t0.plusMinutes(1), "환불 확인"))
        whenever(repository.now()).thenReturn(t0.plusMinutes(1))

        mockMvc.post("/api/v1/violations/5/resolve") {
            header(HttpHeaders.AUTHORIZATION, adminAuthorization)
            contentType = MediaType.APPLICATION_JSON
            content = """{"resolution_note":"환불 확인"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resolution_note") { value("환불 확인") }
            jsonPath("$.resolved_at") { exists() }
        }
        verify(repository).resolve(5L, t0.plusMinutes(1), "7", "환불 확인")
    }
}
