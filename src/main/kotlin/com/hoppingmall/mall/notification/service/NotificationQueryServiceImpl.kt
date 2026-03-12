package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.dto.response.NotificationResponse
import com.hoppingmall.mall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.mall.notification.enum.NotificationType
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

    override fun getUnreadCount(userId: Long): UnreadCountResponse {
        val count = notificationRepository.countByUserIdAndIsRead(userId, false)
        return UnreadCountResponse(count)
    }
}
