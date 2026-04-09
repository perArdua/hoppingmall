package com.hoppingmall.product.product.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.product.product.dto.response.BulkImportProgressResponse
import com.hoppingmall.product.product.dto.response.BulkRowError
import com.hoppingmall.product.product.dto.response.BulkValidationResponse
import com.hoppingmall.product.product.enum.BulkImportStatus
import com.hoppingmall.product.product.service.BulkImportService
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile

@DisplayName("ProductBulkController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductBulkControllerTest {

    @Mock
    private lateinit var bulkImportService: BulkImportService

    @InjectMocks
    private lateinit var controller: ProductBulkController

    private val principal = UserPrincipal(1L, "SELLER")

    @Test
    fun CSV_검증을_수행한다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val response = BulkValidationResponse(
            totalRows = 1, validRows = 1, invalidRows = 0, errors = emptyList(), preview = emptyList()
        )

        whenever(bulkImportService.validate(any())).thenReturn(response)

        val result = controller.validateCsv(file)

        assertThat(result.data!!.totalRows).isEqualTo(1)
    }

    @Test
    fun CSV_대량_등록을_시작한다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val response = BulkImportProgressResponse(
            jobId = 1L, status = BulkImportStatus.IMPORTING,
            totalRows = 10, processedRows = 0, successCount = 0, failCount = 0, completedAt = null
        )

        whenever(bulkImportService.startImport(eq(1L), any())).thenReturn(response)

        val result = controller.importCsv(file, principal)

        assertThat(result.data!!.jobId).isEqualTo(1L)
    }

    @Test
    fun 작업_진행_상태를_조회한다() {
        val response = BulkImportProgressResponse(
            jobId = 1L, status = BulkImportStatus.COMPLETED,
            totalRows = 10, processedRows = 10, successCount = 8, failCount = 2, completedAt = null
        )

        whenever(bulkImportService.getJobProgress(1L, 1L)).thenReturn(response)

        val result = controller.getJobProgress(1L, principal)

        assertThat(result.data!!.successCount).isEqualTo(8)
    }

    @Test
    fun 작업_에러를_조회한다() {
        val errors = listOf(BulkRowError(1, "name", "에러"))

        whenever(bulkImportService.getJobErrors(1L, 1L)).thenReturn(errors)

        val result = controller.getJobErrors(1L, principal)

        assertThat(result.data).hasSize(1)
    }
}
