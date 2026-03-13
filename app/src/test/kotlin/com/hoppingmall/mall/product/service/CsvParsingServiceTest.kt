package com.hoppingmall.mall.product.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal

@DisplayName("CsvParsingService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CsvParsingServiceTest {

    private val csvParsingService = CsvParsingService()

    private fun csvFile(content: String, fileName: String = "products.csv"): MockMultipartFile {
        return MockMultipartFile("file", fileName, "text/csv", content.toByteArray())
    }

    @Nested
    @DisplayName("parse")
    inner class Parse {

        @Test
        fun 정상_CSV_파일을_파싱한다() {
            val csv = "name,description,categoryId,price,stockQuantity,imageUrls\n" +
                    "상품1,설명1,1,15000,100,http://img1.jpg|http://img2.jpg\n" +
                    "상품2,설명2,2,25000,50,"

            val result = csvParsingService.parse(csvFile(csv))

            assertEquals(2, result.rows.size)
            assertTrue(result.errors.isEmpty())

            val row1 = result.rows[0]
            assertEquals(1, row1.rowNumber)
            assertEquals("상품1", row1.name)
            assertEquals("설명1", row1.description)
            assertEquals(1L, row1.categoryId)
            assertEquals(BigDecimal("15000"), row1.price)
            assertEquals(100, row1.stockQuantity)
            assertEquals(listOf("http://img1.jpg", "http://img2.jpg"), row1.imageUrls)

            val row2 = result.rows[1]
            assertEquals(2, row2.rowNumber)
            assertTrue(row2.imageUrls.isEmpty())
        }

        @Test
        fun 필수_헤더가_누락되면_에러를_반환한다() {
            val csv = "name,description,price\n" +
                    "상품1,설명1,15000"

            val result = csvParsingService.parse(csvFile(csv))

            assertTrue(result.rows.isEmpty())
            assertEquals(1, result.errors.size)
            assertEquals("header", result.errors[0].field)
        }

        @Test
        fun 유효하지_않은_값이_있으면_행별_에러를_반환한다() {
            val csv = "name,description,categoryId,price,stockQuantity\n" +
                    ",설명1,1,15000,100\n" +
                    "상품2,설명2,abc,15000,100\n" +
                    "상품3,설명3,1,-500,100\n" +
                    "상품4,설명4,1,15000,-1"

            val result = csvParsingService.parse(csvFile(csv))

            assertEquals(0, result.rows.size)
            assertEquals(4, result.errors.size)
            assertEquals("name", result.errors[0].field)
            assertEquals("categoryId", result.errors[1].field)
            assertEquals("price", result.errors[2].field)
            assertEquals("stockQuantity", result.errors[3].field)
        }

        @Test
        fun 정상_행과_에러_행이_혼합되면_각각_분리한다() {
            val csv = "name,description,categoryId,price,stockQuantity\n" +
                    "정상상품,설명,1,15000,100\n" +
                    ",설명2,1,15000,100"

            val result = csvParsingService.parse(csvFile(csv))

            assertEquals(1, result.rows.size)
            assertEquals(1, result.errors.size)
            assertEquals("정상상품", result.rows[0].name)
        }

        @Test
        fun imageUrls_없는_헤더도_정상_파싱한다() {
            val csv = "name,description,categoryId,price,stockQuantity\n" +
                    "상품1,설명1,1,15000,100"

            val result = csvParsingService.parse(csvFile(csv))

            assertEquals(1, result.rows.size)
            assertTrue(result.rows[0].imageUrls.isEmpty())
        }
    }
}
