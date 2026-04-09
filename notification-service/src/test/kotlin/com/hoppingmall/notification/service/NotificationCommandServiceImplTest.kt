package com.hoppingmall.notification.service

import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.exception.NotificationAccessDeniedException
import com.hoppingmall.notification.exception.NotificationNotFoundException
import com.hoppingmall.notification.support.fixture.createNotification
import com.hoppingmall.notification.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationCommandServiceImplTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @InjectMocks
    private lateinit var notificationCommandService: NotificationCommandServiceImpl

    @Test
    fun 알림을_읽음_처리한다() {
        val notification = createNotification(userId = 1L).withId(1L)
        whenever(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))

        notificationCommandService.markAsRead(1L, 1L)

        assertThat(notification.isRead).isTrue()
    }

    @Test
    fun 존재하지_않는_알림을_읽음_처리하면_예외가_발생한다() {
        whenever(notificationRepository.findById(1L)).thenReturn(Optional.empty())

        assertThatThrownBy { notificationCommandService.markAsRead(1L, 1L) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun 다른_사용자의_알림을_읽으면_접근_거부_예외가_발생한다() {
        val notification = createNotification(userId = 1L).withId(1L)
        whenever(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))

        assertThatThrownBy { notificationCommandService.markAsRead(1L, 999L) }
            .isInstanceOf(NotificationAccessDeniedException::class.java)
    }

    @Test
    fun 모든_알림을_읽음_처리한다() {
        notificationCommandService.markAllAsRead(1L)

        verify(notificationRepository).markAllAsRead(1L)
    }
}
