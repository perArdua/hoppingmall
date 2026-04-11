package com.hoppingmall.notification.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.exception.NotificationAccessDeniedException
import com.hoppingmall.notification.exception.NotificationNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NotificationCommandServiceImpl(
    private val notificationRepository: NotificationRepository
) : NotificationCommandService {

    @CacheEvict(cacheNames = ["unread-count"], key = "#userId")
    override fun markAsRead(notificationId: Long, userId: Long) {
        val notification = notificationRepository.findByIdOrNull(notificationId) ?: throw NotificationNotFoundException() 

        if (notification.userId != userId) {
            throw NotificationAccessDeniedException()
        }

        notification.markAsRead()
    }

    @CacheEvict(cacheNames = ["unread-count"], key = "#userId")
    override fun markAllAsRead(userId: Long) {
        notificationRepository.markAllAsRead(userId)
    }
}
