package com.hoppingmall.notification.exception

import com.hoppingmall.notification.common.BusinessException
import com.hoppingmall.notification.exception.code.NotificationErrorCode

open class NotificationException(
    errorCode: NotificationErrorCode
) : BusinessException(errorCode)
