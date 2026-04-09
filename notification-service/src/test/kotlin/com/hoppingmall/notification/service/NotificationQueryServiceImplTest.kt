package com.hoppingmall.notification.service

import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.notification.enums.NotificationType
import com.hoppingmall.notification.support.fixture.createNotification
import com.hoppingmall.notification.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationQueryServiceImplTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @InjectMocks
    private lateinit var notificationQueryService: NotificationQueryServiceImpl

    @Test
    fun 사용자의_알림_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)
        val notifications = listOf(
            createNotification(eventId = "e1", userId = 1L).withId(1L),
            createNotification(eventId = "e2", userId = 1L).withId(2L)
        )
        whenever(notificationRepository.findByUserId(1L, pageable))
            .thenReturn(SliceImpl(notifications, pageable, false))

        val result = notificationQueryService.getNotifications(1L, null, pageable)

        assertThat(result.content).hasSize(2)
    }

    @Test
    fun 타입별_알림_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)
        val type = NotificationType.PAYMENT_COMPLETED
        val notifications = listOf(
            createNotification(eventId = "e1", userId = 1L, type = type).withId(1L)
        )
        whenever(notificationRepository.findByUserIdAndType(1L, type, pageable))
            .thenReturn(SliceImpl(notifications, pageable, false))

        val result = notificationQueryService.getNotifications(1L, type, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 읽지_않은_알림_수를_조회한다() {
        whenever(notificationRepository.countByUserIdAndIsRead(1L, false)).thenReturn(5L)

        val result = notificationQueryService.getUnreadCount(1L)

        assertThat(result).isEqualTo(UnreadCountResponse(5L))
    }
}
