package com.hoppingmall.user.common

import jakarta.persistence.*
import org.hibernate.annotations.FilterDef
import org.hibernate.annotations.ParamDef
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@FilterDef(
    name = "softDeleteFilter",
    parameters = [ParamDef(name = "isDeleted", type = Boolean::class)]
)
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null

    @Column
    var deletedAt: LocalDateTime? = null

    fun softDelete() {
        this.deletedAt = LocalDateTime.now()
    }
}
