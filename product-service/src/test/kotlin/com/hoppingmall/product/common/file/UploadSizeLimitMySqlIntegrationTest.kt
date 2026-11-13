package com.hoppingmall.product.common.file

import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties

@DisplayName("업로드 크기 한도 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class UploadSizeLimitMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var multipartProperties: MultipartProperties

    @Autowired
    private lateinit var fileUploadConfig: FileUploadConfig

    @Test
    fun 서블릿_파일_한도는_앱_업로드_한도와_같다() {
        assertThat(multipartProperties.maxFileSize.toBytes()).isEqualTo(fileUploadConfig.maxFileSize)
        assertThat(fileUploadConfig.maxFileSize).isEqualTo(5L * 1024 * 1024)
    }

    @Test
    fun 서블릿_요청_한도는_파일_한도보다_작지_않다() {
        assertThat(multipartProperties.maxRequestSize.toBytes()).isGreaterThanOrEqualTo(multipartProperties.maxFileSize.toBytes())
    }
}
