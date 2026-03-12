package com.hoppingmall.mall.notification.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.notification.exception.code.NotificationErrorCode

open class NotificationException(
    errorCode: NotificationErrorCode
) : BusinessException(errorCode)
