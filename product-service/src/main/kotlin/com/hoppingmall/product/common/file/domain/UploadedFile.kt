package com.hoppingmall.product.common.file.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.EnumNameConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "uploaded_files",
    uniqueConstraints = [UniqueConstraint(name = "uk_uploaded_files_object_key", columnNames = ["object_key"])],
    indexes = [Index(name = "idx_uploaded_files_status_created_at", columnList = "status, created_at")]
)
class UploadedFile private constructor(

    @Column(name = "object_key", nullable = false, length = 255)
    val objectKey: String,

    @Column(nullable = false)
    val uploaderId: Long,

    @Column(nullable = false, length = 20)
    val uploaderRole: String,

    @Column(nullable = false, length = 50)
    val contentType: String,

    @Column(nullable = false)
    val fileSize: Long,

    @Convert(converter = UploadStatusConverter::class)
    @Column(nullable = false, length = 20)
    var status: UploadStatus = UploadStatus.PENDING,

    var attachedAt: LocalDateTime? = null

) : BaseEntity() {

    fun canBeLinkedBy(sellerId: Long): Boolean = uploaderRole == ADMIN_ROLE || uploaderId == sellerId

    fun attach(now: LocalDateTime) {
        if (status == UploadStatus.ATTACHED) return
        status = UploadStatus.ATTACHED
        attachedAt = now
    }

    companion object {
        private const val ADMIN_ROLE = "ADMIN"

        fun pending(
            objectKey: String,
            uploaderId: Long,
            uploaderRole: String,
            contentType: String,
            fileSize: Long
        ): UploadedFile = UploadedFile(objectKey, uploaderId, uploaderRole, contentType, fileSize)
    }
}

class UploadStatusConverter : EnumNameConverter<UploadStatus>(UploadStatus::class.java)
