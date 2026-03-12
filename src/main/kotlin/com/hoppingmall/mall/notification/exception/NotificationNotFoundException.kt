package com.hoppingmall.mall.notification.exception

import com.hoppingmall.mall.notification.exception.code.NotificationErrorCode

class NotificationNotFoundException : NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND)
