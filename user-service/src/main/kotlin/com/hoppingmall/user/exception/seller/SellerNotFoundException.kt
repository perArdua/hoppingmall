package com.hoppingmall.user.exception.seller

import com.hoppingmall.common.BusinessException

class SellerNotFoundException : BusinessException(SellerErrorCode.SELLER_NOT_FOUND)
