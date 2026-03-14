package com.hoppingmall.user.exception.seller

import com.hoppingmall.user.common.BusinessException

class SellerInvalidApprovalCommandException :
    BusinessException(SellerErrorCode.SELLER_INVALID_APPROVAL_COMMAND)
