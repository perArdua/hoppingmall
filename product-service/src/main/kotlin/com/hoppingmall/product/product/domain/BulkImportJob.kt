package com.hoppingmall.product.product.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.product.product.enum.BulkImportStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "bulk_import_jobs",
    indexes = [Index(name = "idx_bulk_import_jobs_seller_id", columnList = "sellerId")]
)
class BulkImportJob private constructor(
    @Column(nullable = false)
    val sellerId: Long,

    @Column(nullable = false)
    val fileName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BulkImportStatus = BulkImportStatus.VALIDATING,

    @Column(nullable = false)
    var totalRows: Int = 0,

    @Column(nullable = false)
    var processedRows: Int = 0,

    @Column(nullable = false)
    var successCount: Int = 0,

    @Column(nullable = false)
    var failCount: Int = 0,

    @Column(columnDefinition = "TEXT")
    var errorDetails: String? = null,

    @Column
    var completedAt: LocalDateTime? = null
) : BaseEntity() {

    companion object {
        fun create(sellerId: Long, fileName: String, totalRows: Int): BulkImportJob {
            return BulkImportJob(
                sellerId = sellerId,
                fileName = fileName,
                totalRows = totalRows
            )
        }
    }

    fun startImport() {
        this.status = BulkImportStatus.IMPORTING
    }

    fun recordSuccess() {
        this.processedRows++
        this.successCount++
    }

    fun recordFailure() {
        this.processedRows++
        this.failCount++
    }

    fun complete() {
        this.status = BulkImportStatus.COMPLETED
        this.completedAt = LocalDateTime.now()
    }

    fun fail() {
        this.status = BulkImportStatus.FAILED
        this.completedAt = LocalDateTime.now()
    }

    fun validated() {
        this.status = BulkImportStatus.VALIDATED
    }
}
