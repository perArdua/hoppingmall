package com.hoppingmall.product.product.domain

import com.hoppingmall.product.product.enum.BulkImportStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("BulkImportJob 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class BulkImportJobTest {

    @Test
    fun 대량_등록_작업을_생성한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 100)

        assertThat(job.sellerId).isEqualTo(1L)
        assertThat(job.fileName).isEqualTo("test.csv")
        assertThat(job.totalRows).isEqualTo(100)
        assertThat(job.status).isEqualTo(BulkImportStatus.VALIDATING)
    }

    @Test
    fun 등록_시작_상태로_변경한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 100)

        job.startImport()

        assertThat(job.status).isEqualTo(BulkImportStatus.IMPORTING)
    }

    @Test
    fun 성공_건수를_기록한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 100)

        job.recordSuccess()

        assertThat(job.processedRows).isEqualTo(1)
        assertThat(job.successCount).isEqualTo(1)
    }

    @Test
    fun 실패_건수를_기록한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 100)

        job.recordFailure()

        assertThat(job.processedRows).isEqualTo(1)
        assertThat(job.failCount).isEqualTo(1)
    }

    @Test
    fun 완료_상태로_변경한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 100)

        job.complete()

        assertThat(job.status).isEqualTo(BulkImportStatus.COMPLETED)
        assertThat(job.completedAt).isNotNull()
    }

    @Test
    fun 실패_상태로_변경한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 100)

        job.fail()

        assertThat(job.status).isEqualTo(BulkImportStatus.FAILED)
        assertThat(job.completedAt).isNotNull()
    }

    @Test
    fun 검증_완료_상태로_변경한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 100)

        job.validated()

        assertThat(job.status).isEqualTo(BulkImportStatus.VALIDATED)
    }
}
