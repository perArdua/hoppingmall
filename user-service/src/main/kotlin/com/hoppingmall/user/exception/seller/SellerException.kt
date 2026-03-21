package com.hoppingmall.user.exception.seller

import com.hoppingmall.common.BusinessException

open class SellerException(
    errorCode: SellerErrorCode
) : BusinessException(errorCode)
