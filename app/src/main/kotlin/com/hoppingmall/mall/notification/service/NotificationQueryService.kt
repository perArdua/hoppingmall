package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.dto.response.NotificationResponse
import com.hoppingmall.mall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.mall.notification.enum.NotificationType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface NotificationQueryService {
    fun getNotifications(userId: Long, type: NotificationType?, pageable: Pageable): Slice<NotificationResponse>
    fun getUnreadCount(userId: Long): UnreadCountResponse
}
