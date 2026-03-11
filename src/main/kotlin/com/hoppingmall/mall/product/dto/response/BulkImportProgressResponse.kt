package com.hoppingmall.mall.product.dto.response

import com.hoppingmall.mall.product.domain.BulkImportJob
import com.hoppingmall.mall.product.enum.BulkImportStatus
import java.time.LocalDateTime

data class BulkImportProgressResponse(
    val jobId: Long,
    val status: BulkImportStatus,
    val totalRows: Int,
    val processedRows: Int,
    val successCount: Int,
    val failCount: Int,
    val completedAt: LocalDateTime?
) {
    companion object {
        fun from(job: BulkImportJob): BulkImportProgressResponse {
            return BulkImportProgressResponse(
                jobId = job.id!!,
                status = job.status,
                totalRows = job.totalRows,
                processedRows = job.processedRows,
                successCount = job.successCount,
                failCount = job.failCount,
                completedAt = job.completedAt
            )
        }
    }
}
