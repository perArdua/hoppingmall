package com.hoppingmall.notification.service

import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.notification.enums.NotificationType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface NotificationQueryService {
    fun getNotifications(userId: Long, type: NotificationType?, pageable: Pageable): Slice<NotificationResponse>
    fun getUnreadCount(userId: Long): UnreadCountResponse
}
