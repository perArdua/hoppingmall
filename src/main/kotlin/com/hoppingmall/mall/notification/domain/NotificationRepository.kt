package com.hoppingmall.mall.notification.domain

import com.hoppingmall.mall.notification.enum.NotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {
    
    fun findByUserId(userId: Long): List<Notification>
    
    fun findByUserId(userId: Long, pageable: Pageable): Page<Notification>
    
    fun findByUserIdAndType(userId: Long, type: NotificationType): List<Notification>
    
    fun findByUserIdAndIsRead(userId: Long, isRead: Boolean): List<Notification>
    
    fun countByUserIdAndIsRead(userId: Long, isRead: Boolean): Long
} 