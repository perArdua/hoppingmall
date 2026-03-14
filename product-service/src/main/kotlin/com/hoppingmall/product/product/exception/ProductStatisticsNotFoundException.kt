package com.hoppingmall.product.product.exception

import com.hoppingmall.product.common.BusinessException
import com.hoppingmall.product.product.exception.code.ProductErrorCode

class ProductStatisticsNotFoundException : BusinessException(ProductErrorCode.PRODUCT_STATISTICS_NOT_FOUND)
