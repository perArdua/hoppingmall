package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.product.domain.BulkImportJob
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BulkImportJobRepository : JpaRepository<BulkImportJob, Long> {

    fun findBySellerIdOrderByCreatedAtDesc(sellerId: Long): List<BulkImportJob>
}
