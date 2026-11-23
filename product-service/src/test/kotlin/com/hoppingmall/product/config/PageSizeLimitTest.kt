package com.hoppingmall.product.config

import com.hoppingmall.product.support.TestCacheConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@DisplayName("목록·통계 입구의 page·size·limit·days 범위")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class PageSizeLimitTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @ParameterizedTest
    @CsvSource(
        "/api/v1/products,size,101",
        "/api/v1/products,size,0",
        "/api/v1/products,size,abc",
        "/api/v1/products,page,-1",
        "/api/v1/products/search?keyword=x,size,2000",
        "/api/v1/products/category/1,size,500"
    )
    fun Pageable_목록의_범위_밖_값은_조용히_바꾸지_않고_400과_필드_오류다(path: String, field: String, value: String) {
        mockMvc.get(path) { param(field, value) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value(field) }
        }
    }

    @Test
    fun Pageable_목록의_상한인_size_100은_받는다() {
        mockMvc.get("/api/v1/products") { param("size", "100") }.andExpect {
            status { isOk() }
            jsonPath("$.data.size") { value(100) }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "/api/v1/admin/product-statistics/top-selling,days,0",
        "/api/v1/admin/product-statistics/top-selling,days,366",
        "/api/v1/admin/product-statistics/top-selling,limit,0",
        "/api/v1/admin/product-statistics/top-selling,limit,101",
        "/api/v1/admin/product-statistics/top-refund,days,-7",
        "/api/v1/admin/product-statistics/top-refund,limit,101",
        "/api/v1/admin/product-statistics/peak-hours,days,0",
        "/api/v1/admin/product-statistics/peak-hours,days,366"
    )
    fun 통계의_기간과_개수가_범위_밖이면_400과_필드_오류다(path: String, field: String, value: String) {
        mockMvc.get(path) {
            header("X-User-Id", "9301")
            header("x-user-role", "ADMIN")
            param(field, value)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value(field) }
        }
    }
}
