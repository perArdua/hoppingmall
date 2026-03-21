package com.hoppingmall.user.exception.seller

import com.hoppingmall.common.BusinessException

class SellerInvalidApprovalStatusException :
    BusinessException(SellerErrorCode.INVALID_APPROVAL_STATUS)
