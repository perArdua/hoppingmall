package com.hoppingmall.mall.user.exception.seller

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.global.common.error.code.ErrorCode

class SellerInvalidApprovalCommandException:
    BusinessException(SellerErrorCode.SELLER_INVALID_APPROVAL_COMMAND)

