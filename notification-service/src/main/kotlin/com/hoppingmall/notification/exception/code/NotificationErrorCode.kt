package com.hoppingmall.notification.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class NotificationErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    NOTIFICATION_NOT_FOUND("NTF001", "알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    NOTIFICATION_ACCESS_DENIED("NTF002", "알림에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN)
}
