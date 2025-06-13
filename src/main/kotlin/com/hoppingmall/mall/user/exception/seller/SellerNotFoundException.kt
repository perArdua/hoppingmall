package com.hoppingmall.mall.user.exception.seller

import com.hoppingmall.mall.global.common.error.exception.BusinessException

class SellerNotFoundException : BusinessException(SellerErrorCode.SELLER_NOT_FOUND)
