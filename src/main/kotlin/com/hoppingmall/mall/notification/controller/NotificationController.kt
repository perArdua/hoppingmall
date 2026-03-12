package com.hoppingmall.mall.notification.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.notification.dto.response.NotificationResponse
import com.hoppingmall.mall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.notification.service.NotificationCommandService
import com.hoppingmall.mall.notification.service.NotificationQueryService
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationQueryService: NotificationQueryService,
    private val notificationCommandService: NotificationCommandService
) {

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
