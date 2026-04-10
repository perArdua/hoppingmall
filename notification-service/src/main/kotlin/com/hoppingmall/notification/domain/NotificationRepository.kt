package com.hoppingmall.notification.domain

import com.hoppingmall.notification.enums.NotificationType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {

    fun existsByEventId(eventId: String): Boolean

    fun findByUserId(userId: Long, pageable: Pageable): Slice<Notification>

    fun findByUserIdAndType(userId: Long, type: NotificationType, pageable: Pageable): Slice<Notification>

    fun countByUserIdAndIsRead(userId: Long, isRead: Boolean): Long

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    fun markAllAsRead(@Param("userId") userId: Long): Int
}
