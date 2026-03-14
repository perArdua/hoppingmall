package com.hoppingmall.mall.product.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.product.exception.code.ProductErrorCode

class ProductNotFoundException : BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)