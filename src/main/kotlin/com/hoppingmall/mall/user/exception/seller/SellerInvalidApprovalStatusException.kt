package com.hoppingmall.mall.user.exception.seller

import com.hoppingmall.mall.global.common.error.exception.BusinessException

class SellerInvalidApprovalStatusException :
    BusinessException(SellerErrorCode.INVALID_APPROVAL_STATUS)
