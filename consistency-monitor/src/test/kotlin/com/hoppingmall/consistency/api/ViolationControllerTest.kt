package com.hoppingmall.consistency.api

import com.hoppingmall.consistency.violation.Violation
import com.hoppingmall.consistency.violation.ViolationRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(ViolationController::class)
@DisplayName("ViolationController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ViolationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var repository: ViolationRepository

    private val t0 = LocalDateTime.of(2026, 10, 22, 19, 0, 0)

    private fun violation(resolvedAt: LocalDateTime? = null, note: String? = null) =
        Violation(5L, "INV-1", "10", "CRITICAL", t0, t0, t0, resolvedAt, null, note, "{}")

    @Test
    fun 열린_위반_목록을_조회한다() {
        whenever(repository.findAll(true)).thenReturn(listOf(violation()))

        mockMvc.get("/api/v1/violations").andExpect {
            status { isOk() }
            jsonPath("$[0].invId") { value("INV-1") }
        }
    }

    @Test
    fun 사유와_담당자를_적으면_수동으로_닫는다() {
        whenever(repository.findById(5L)).thenReturn(violation(), violation(t0.plusMinutes(1), "환불 확인"))
        whenever(repository.now()).thenReturn(t0.plusMinutes(1))

        mockMvc.post("/api/v1/violations/5/resolve") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ackedBy":"kim","resolutionNote":"환불 확인"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resolutionNote") { value("환불 확인") }
        }
        verify(repository).resolve(5L, t0.plusMinutes(1), "kim", "환불 확인")
    }

    @Test
    fun 사유가_없으면_닫을_수_없다() {
        mockMvc.post("/api/v1/violations/5/resolve") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ackedBy":"kim","resolutionNote":" "}"""
        }.andExpect { status { isBadRequest() } }
        verify(repository, never()).resolve(any(), any(), any(), any())
    }

    @Test
    fun 없는_위반은_404() {
        whenever(repository.findById(9L)).thenReturn(null)

        mockMvc.post("/api/v1/violations/9/resolve") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ackedBy":"kim","resolutionNote":"x"}"""
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun 이미_해소된_위반은_409() {
        whenever(repository.findById(5L)).thenReturn(violation(t0, "auto"))

        mockMvc.post("/api/v1/violations/5/resolve") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ackedBy":"kim","resolutionNote":"x"}"""
        }.andExpect { status { isConflict() } }
    }
}
