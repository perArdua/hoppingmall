package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.exception.NotificationAccessDeniedException
import com.hoppingmall.mall.notification.exception.NotificationNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NotificationCommandServiceImpl(
    private val notificationRepository: NotificationRepository
) : NotificationCommandService {

    override fun markAsRead(notificationId: Long, userId: Long) {
        val notification = notificationRepository.findById(notificationId)
            .orElseThrow { NotificationNotFoundException() }

        if (notification.userId != userId) {
            throw NotificationAccessDeniedException()
        }

        notification.markAsRead()
    }

    override fun markAllAsRead(userId: Long) {
        notificationRepository.markAllAsRead(userId)
    }
}
