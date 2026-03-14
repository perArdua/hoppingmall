package com.hoppingmall.notification.controller

import com.hoppingmall.notification.common.ApiResponse
import com.hoppingmall.notification.common.UserPrincipal
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.notification.enums.NotificationType
import com.hoppingmall.notification.service.NotificationCommandService
import com.hoppingmall.notification.service.NotificationQueryService
import com.hoppingmall.notification.service.NotificationSseService
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationQueryService: NotificationQueryService,
    private val notificationCommandService: NotificationCommandService,
    private val notificationSseService: NotificationSseService
) {

    @GetMapping("/subscribe", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): SseEmitter {
        return notificationSseService.connect(userPrincipal.getUserId())
    }

    @GetMapping
    fun getNotifications(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @RequestParam(required = false) type: NotificationType?,
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable
    ): ResponseEntity<ApiResponse<Slice<NotificationResponse>>> {
        val notifications = notificationQueryService.getNotifications(
            userPrincipal.getUserId(), type, pageable
        )
        return ResponseEntity.ok(ApiResponse.success(notifications))
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<ApiResponse<UnreadCountResponse>> {
        val count = notificationQueryService.getUnreadCount(userPrincipal.getUserId())
        return ResponseEntity.ok(ApiResponse.success(count))
    }

    @PatchMapping("/{notificationId}/read")
    fun markAsRead(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable notificationId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationCommandService.markAsRead(notificationId, userPrincipal.getUserId())
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @PatchMapping("/read-all")
    fun markAllAsRead(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationCommandService.markAllAsRead(userPrincipal.getUserId())
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
}
