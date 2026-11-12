package com.hoppingmall.common

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.resource.NoResourceFoundException

data class RequestErrorTestBody(val name: String)

@RestController
class RequestErrorTestController {
    @GetMapping("/request-error/items/{id}")
    fun item(@PathVariable("id") id: Long): Long = id

    @GetMapping("/request-error/param")
    fun param(@RequestParam("quantity") quantity: Int): Int = quantity

    @PostMapping("/request-error/body")
    fun body(@RequestBody body: RequestErrorTestBody): String = body.name

    @GetMapping("/request-error/header")
    fun header(@RequestHeader("Authorization") bearer: String): String = bearer

    @GetMapping("/request-error/static")
    fun missingResource(): String = throw NoResourceFoundException(HttpMethod.GET, "request-error/static")
}

@DisplayName("GlobalExceptionHandler 요청 오류 응답 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerRequestErrorTest {

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(RequestErrorTestController())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun 경로_변수_형_변환에_실패하면_400과_C001을_응답한다() {
        mockMvc.perform(get("/request-error/items/abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun 필수_파라미터가_없으면_400과_C001을_응답한다() {
        mockMvc.perform(get("/request-error/param"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun 파라미터_형_변환에_실패하면_400과_C001을_응답한다() {
        mockMvc.perform(get("/request-error/param").param("quantity", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun 본문_JSON이_깨지면_400과_C001을_응답한다() {
        mockMvc.perform(post("/request-error/body").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun 본문이_없으면_400과_C001을_응답한다() {
        mockMvc.perform(post("/request-error/body").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun 지원하지_않는_Content_Type이면_415와_REQ003을_응답한다() {
        mockMvc.perform(post("/request-error/body").contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.code").value("REQ003"))
            .andExpect(header().exists("Accept"))
    }

    @Test
    fun 지원하지_않는_메서드면_405와_REQ002를_응답한다() {
        mockMvc.perform(delete("/request-error/items/1"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("REQ002"))
            .andExpect(header().exists("Allow"))
    }

    @Test
    fun 필수_헤더가_없으면_400과_C001을_응답한다() {
        mockMvc.perform(get("/request-error/header"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun 처리할_곳이_없는_경로면_404와_REQ001을_응답한다() {
        mockMvc.perform(get("/request-error/unknown"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REQ001"))
    }

    @Test
    fun 정적_자원을_찾지_못하면_404와_REQ001을_응답한다() {
        mockMvc.perform(get("/request-error/static"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REQ001"))
    }

    @Test
    fun 정상_요청은_그대로_200을_응답한다() {
        mockMvc.perform(get("/request-error/items/1"))
            .andExpect(status().isOk)
    }
}
