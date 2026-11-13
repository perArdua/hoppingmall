package com.hoppingmall.notification.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.notification.enums.NotificationType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
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
