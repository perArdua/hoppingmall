package com.hoppingmall.common.page

import com.hoppingmall.common.GlobalExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.data.domain.Pageable
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PageRequestLimitTestController {
    @GetMapping("/page-limit/pageable")
    fun pageable(pageable: Pageable): String = "${pageable.pageNumber}:${pageable.pageSize}"

    @GetMapping("/page-limit/plain")
    fun plain(@RequestParam(defaultValue = "500") size: Int): Int = size
}

@DisplayName("목록 page·size 범위 공통 검사")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PageRequestLimitTest {

    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration::class.java,
                HttpMessageConvertersAutoConfiguration::class.java,
                WebMvcAutoConfiguration::class.java,
                SpringDataWebAutoConfiguration::class.java
            )
        )
        .withUserConfiguration(PageRequestLimitConfig::class.java, GlobalExceptionHandler::class.java, PageRequestLimitTestController::class.java)

    private fun withMockMvc(block: (MockMvc) -> Unit) {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            block(MockMvcBuilders.webAppContextSetup(context).build())
        }
    }

    @ParameterizedTest
    @CsvSource(
        "size,101,100 이하여야 합니다",
        "size,0,1 이상이어야 합니다",
        "size,-3,1 이상이어야 합니다",
        "page,-1,0 이상이어야 합니다",
        "size,abc,형식이 올바르지 않습니다.",
        "page,1.5,형식이 올바르지 않습니다."
    )
    fun Pageable_입구의_범위_밖_값은_400과_필드_오류다(field: String, value: String, reason: String) = withMockMvc { mockMvc ->
        mockMvc.get("/page-limit/pageable") { param(field, value) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors.length()") { value(1) }
            jsonPath("$.errors[0].field") { value(field) }
            jsonPath("$.errors[0].reason") { value(reason) }
        }
    }

    @Test
    fun page와_size가_함께_틀리면_둘_다_알린다() = withMockMvc { mockMvc ->
        mockMvc.get("/page-limit/pageable") {
            param("page", "-1")
            param("size", "1000")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("page") }
            jsonPath("$.errors[1].field") { value("size") }
        }
    }

    @ParameterizedTest
    @CsvSource("0,1", "3,100", "'',''")
    fun Pageable_입구의_범위_안_값과_빈_값은_그대로_받는다(page: String, size: String) = withMockMvc { mockMvc ->
        val expected = "${page.ifEmpty { "0" }}:${size.ifEmpty { "20" }}"
        mockMvc.get("/page-limit/pageable") {
            param("page", page)
            param("size", size)
        }.andExpect {
            status { isOk() }
            content { string(expected) }
        }
    }

    @Test
    fun Pageable이_없는_입구는_검사하지_않는다() = withMockMvc { mockMvc ->
        mockMvc.get("/page-limit/plain") { param("size", "500") }.andExpect {
            status { isOk() }
            content { string("500") }
        }
    }

    @Test
    fun 직접_받는_page와_size를_검사한다() {
        PageRequestLimits.requireValid(0, PageRequestLimits.MAX_PAGE_SIZE)

        assertThatThrownBy { PageRequestLimits.requireValid(-1, 101) }
            .isInstanceOfSatisfying(RequestRangeException::class.java) { ex ->
                assertThat(ex.errors.map { it.field to it.reason }).containsExactly(
                    "page" to "0 이상이어야 합니다",
                    "size" to "100 이하여야 합니다"
                )
            }
    }

    @Test
    fun 다른_이름과_범위도_같은_방식으로_검사한다() {
        assertThatThrownBy { PageRequestLimits.requireInRange(RangeCheck("days", 400, 1, 365), RangeCheck("limit", 5, 1, 100)) }
            .isInstanceOfSatisfying(RequestRangeException::class.java) { ex ->
                assertThat(ex.errors.map { it.field }).containsExactly("days")
                assertThat(ex.message).contains("days")
            }
    }
}
