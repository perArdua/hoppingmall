package com.hoppingmall.user.exception.seller

import com.hoppingmall.user.common.BusinessException

class SellerInvalidApprovalStatusException :
    BusinessException(SellerErrorCode.INVALID_APPROVAL_STATUS)
