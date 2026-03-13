package com.hoppingmall.mall.user.exception.seller

import com.hoppingmall.mall.global.common.error.exception.BusinessException


open class SellerException(
    errorCode: SellerErrorCode
) : BusinessException(errorCode)
