package com.hoppingmall.product.product.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.product.exception.code.ProductErrorCode

open class ProductException(
    errorCode: ProductErrorCode
) : BusinessException(errorCode)
