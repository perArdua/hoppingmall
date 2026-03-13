package com.hoppingmall.mall.notification.service

interface NotificationCommandService {
    fun markAsRead(notificationId: Long, userId: Long)
    fun markAllAsRead(userId: Long)
}
