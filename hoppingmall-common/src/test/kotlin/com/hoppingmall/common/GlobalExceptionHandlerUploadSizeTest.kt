package com.hoppingmall.common

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.multipart.MultipartResolver
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@RestController
class UploadSizeTestController {
    @PostMapping("/upload-size/files")
    fun upload(): String = "ok"
}

class SizeLimitedMultipartResolver(private val maxUploadSize: Long) : MultipartResolver {
    override fun isMultipart(request: HttpServletRequest): Boolean =
        request.contentType?.startsWith("multipart/") == true

    override fun resolveMultipart(request: HttpServletRequest): MultipartHttpServletRequest {
        if (request.contentLengthLong > maxUploadSize) {
            throw MaxUploadSizeExceededException(maxUploadSize)
        }
        throw IllegalStateException("not used")
    }

    override fun cleanupMultipart(request: MultipartHttpServletRequest) {}
}

@Configuration
@EnableWebMvc
class UploadSizeTestConfig {
    @Bean
    fun uploadSizeTestController() = UploadSizeTestController()

    @Bean
    fun globalExceptionHandler() = GlobalExceptionHandler()

    @Bean
    fun multipartResolver(): MultipartResolver = SizeLimitedMultipartResolver(10)
}

@DisplayName("GlobalExceptionHandler 업로드 크기 초과 응답 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerUploadSizeTest {

    private val context = AnnotationConfigWebApplicationContext().apply {
        servletContext = MockServletContext()
        register(UploadSizeTestConfig::class.java)
        refresh()
    }

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

    @AfterEach
    fun tearDown() {
        context.close()
    }

    @Test
    fun 업로드_크기_한도를_넘으면_413과_REQ004를_응답한다() {
        mockMvc.perform(
            post("/upload-size/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .content(ByteArray(64))
        )
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("REQ004"))
    }

    @Test
    fun 멀티파트가_아닌_요청은_그대로_200을_응답한다() {
        mockMvc.perform(post("/upload-size/files"))
            .andExpect(status().isOk)
    }
}
