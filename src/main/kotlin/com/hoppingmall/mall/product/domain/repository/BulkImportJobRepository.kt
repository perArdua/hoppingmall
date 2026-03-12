package com.hoppingmall.mall.product.domain.repository

import com.hoppingmall.mall.product.domain.BulkImportJob
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BulkImportJobRepository : JpaRepository<BulkImportJob, Long> {

    fun findBySellerIdOrderByCreatedAtDesc(sellerId: Long): List<BulkImportJob>
}
