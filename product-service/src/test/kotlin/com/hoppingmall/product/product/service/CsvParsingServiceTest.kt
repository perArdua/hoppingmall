package com.hoppingmall.product.product.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

@DisplayName("CsvParsingService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CsvParsingServiceTest {

    private val service = CsvParsingService()

    @Test
    fun 정상적인_CSV를_파싱한다() {
        val csv = "name,description,categoryId,price,stockQuantity\nA,descA,1,1000,10\nB,descB,2,2000,20"
        val file = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

        val result = service.parse(file)

        assertThat(result.rows).hasSize(2)
        assertThat(result.errors).isEmpty()
        assertThat(result.rows[0].name).isEqualTo("A")
        assertThat(result.rows[1].price).isEqualByComparingTo(java.math.BigDecimal("2000"))
    }

    @Test
    fun 필수_헤더_누락_시_에러를_반환한다() {
        val csv = "name,price\nA,1000"
        val file = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

        val result = service.parse(file)

        assertThat(result.rows).isEmpty()
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors[0].field).isEqualTo("header")
    }

    @Test
    fun 유효하지_않은_데이터_행을_에러로_처리한다() {
        val csv = "name,description,categoryId,price,stockQuantity\n,desc,1,1000,10\nB,,abc,-1,-5"
        val file = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

        val result = service.parse(file)

        assertThat(result.rows).isEmpty()
        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun imageUrls_컬럼이_있으면_파싱한다() {
        val csv = "name,description,categoryId,price,stockQuantity,imageUrls\nA,desc,1,1000,10,http://a.jpg|http://b.jpg"
        val file = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

        val result = service.parse(file)

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows[0].imageUrls).containsExactly("http://a.jpg", "http://b.jpg")
    }

    @Test
    fun 빈_imageUrls은_빈_리스트로_파싱한다() {
        val csv = "name,description,categoryId,price,stockQuantity,imageUrls\nA,desc,1,1000,10,"
        val file = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

        val result = service.parse(file)

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows[0].imageUrls).isEmpty()
    }

    @Test
    fun 최대_행_수_초과_시_에러를_추가한다() {
        val header = "name,description,categoryId,price,stockQuantity"
        val rows = (1..1001).joinToString("\n") { "name$it,desc$it,1,1000,10" }
        val csv = "$header\n$rows"
        val file = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

        val result = service.parse(file)

        assertThat(result.errors.any { it.field == "row" }).isTrue()
    }

    @Test
    fun 가격이_0이면_에러를_반환한다() {
        val csv = "name,description,categoryId,price,stockQuantity\nA,desc,1,0,10"
        val file = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

        val result = service.parse(file)

        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun categoryId가_음수이면_에러를_반환한다() {
        val csv = "name,description,categoryId,price,stockQuantity\nA,desc,-1,1000,10"
        val file = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

        val result = service.parse(file)

        assertThat(result.errors).isNotEmpty()
    }
}
