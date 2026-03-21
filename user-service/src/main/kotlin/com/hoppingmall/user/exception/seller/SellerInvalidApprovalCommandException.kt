package com.hoppingmall.user.exception.seller

import com.hoppingmall.common.BusinessException

class SellerInvalidApprovalCommandException :
    BusinessException(SellerErrorCode.SELLER_INVALID_APPROVAL_COMMAND)
