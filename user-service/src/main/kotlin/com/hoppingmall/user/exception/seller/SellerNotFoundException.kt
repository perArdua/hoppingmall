package com.hoppingmall.user.exception.seller

import com.hoppingmall.user.common.BusinessException

class SellerNotFoundException : BusinessException(SellerErrorCode.SELLER_NOT_FOUND)
