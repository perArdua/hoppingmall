package com.hoppingmall.product.category.exception

import com.hoppingmall.product.category.exception.code.CategoryErrorCode
import com.hoppingmall.product.common.BusinessException

class CategoryNotFoundException : BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND)
