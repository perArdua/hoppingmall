package com.hoppingmall.notification.service

import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.notification.enums.NotificationType
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationQueryServiceImpl(
    private val notificationRepository: NotificationRepository
) : NotificationQueryService {

    override fun getNotifications(
        userId: Long,
        type: NotificationType?,
        pageable: Pageable
    ): Slice<NotificationResponse> {
        val notifications = if (type != null) {
            notificationRepository.findByUserIdAndType(userId, type, pageable)
        } else {
            notificationRepository.findByUserId(userId, pageable)
        }
        return notifications.map { NotificationResponse.from(it) }
    }

    @Cacheable(cacheNames = ["unread-count"], key = "#userId")
    override fun getUnreadCount(userId: Long): UnreadCountResponse {
        val count = notificationRepository.countByUserIdAndIsRead(userId, false)
        return UnreadCountResponse(count)
    }
}
