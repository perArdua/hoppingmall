package com.hoppingmall.global.common.entity

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

    @Column
    var updatedAt: LocalDateTime? = LocalDateTime.now()

    @Column
    var deletedAt: LocalDateTime? = null

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}