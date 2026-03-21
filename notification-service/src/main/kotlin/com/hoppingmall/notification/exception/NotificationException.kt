package com.hoppingmall.notification.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.notification.exception.code.NotificationErrorCode

open class NotificationException(
    errorCode: NotificationErrorCode
) : BusinessException(errorCode)
