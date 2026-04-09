package com.hoppingmall.product.product.service

import com.hoppingmall.product.category.domain.Category
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.common.file.FileUploadConfig
import com.hoppingmall.product.product.dto.request.BulkProductRow
import com.hoppingmall.product.product.dto.response.BulkRowError
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal

@DisplayName("BulkImportValidator")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class BulkImportValidatorTest {

    @Mock
    private lateinit var csvParsingService: CsvParsingService

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var fileUploadConfig: FileUploadConfig

    @InjectMocks
    private lateinit var validator: BulkImportValidator

    @Test
    fun 유효한_CSV를_검증한다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val rows = listOf(
            BulkProductRow(1, "상품A", "설명A", 1L, BigDecimal("1000"), 10, emptyList())
        )
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)

        whenever(csvParsingService.parse(any())).thenReturn(CsvParsingService.ParseResult(rows, emptyList()))
        whenever(categoryRepository.findAllById(listOf(1L))).thenReturn(listOf(category))

        val result = validator.validate(file)

        assertThat(result.totalRows).isEqualTo(1)
        assertThat(result.validRows).isEqualTo(1)
        assertThat(result.invalidRows).isEqualTo(0)
    }

    @Test
    fun 존재하지_않는_카테고리_행을_에러로_처리한다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val rows = listOf(
            BulkProductRow(1, "상품A", "설명A", 999L, BigDecimal("1000"), 10, emptyList())
        )

        whenever(csvParsingService.parse(any())).thenReturn(CsvParsingService.ParseResult(rows, emptyList()))
        whenever(categoryRepository.findAllById(listOf(999L))).thenReturn(emptyList())

        val result = validator.validate(file)

        assertThat(result.invalidRows).isEqualTo(1)
        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun 파싱_에러가_있는_CSV를_검증한다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val errors = listOf(BulkRowError(1, "name", "상품명은 필수입니다."))

        whenever(csvParsingService.parse(any())).thenReturn(CsvParsingService.ParseResult(emptyList(), errors))
        whenever(categoryRepository.findAllById(emptyList())).thenReturn(emptyList())

        val result = validator.validate(file)

        assertThat(result.errors).hasSize(1)
    }
}
