package com.hoppingmall.mall.notification.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import com.hoppingmall.mall.notification.enum.NotificationType
import jakarta.persistence.*

@Entity
@Table(
    name = "notifications",
    indexes = [
        Index(name = "idx_notifications_user_id", columnList = "userId"),
        Index(name = "idx_notifications_user_id_is_read", columnList = "userId, isRead")
    ]
)
class Notification(
    @Column(nullable = false, unique = true)
    val eventId: String,

    @Column(nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: NotificationType,

    @Column(nullable = false)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val content: String,

    @Column(columnDefinition = "TEXT")
    val metadata: String? = null,

    @Column
    var isRead: Boolean = false
) : BaseEntity() {

    fun markAsRead() {
        this.isRead = true
    }
}
