package com.hoppingmall.product.common.file.domain.repository

import com.hoppingmall.product.common.file.domain.UploadedFile
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UploadedFileRepository : JpaRepository<UploadedFile, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM UploadedFile f WHERE f.objectKey IN :keys ORDER BY f.id")
    fun findAllByObjectKeyInForUpdate(@Param("keys") keys: Collection<String>): List<UploadedFile>
}
