package com.hoppingmall.product.product.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.product.exception.code.ProductErrorCode

class ProductNotFoundException : BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)