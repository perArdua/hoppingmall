package com.hoppingmall.product.category.exception

import com.hoppingmall.product.category.exception.code.CategoryErrorCode
import com.hoppingmall.common.BusinessException

class CategoryCircularReferenceException : BusinessException(CategoryErrorCode.CATEGORY_CIRCULAR_REFERENCE)
