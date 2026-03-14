package com.hoppingmall.product.category.exception

import com.hoppingmall.product.category.exception.code.CategoryErrorCode
import com.hoppingmall.product.common.BusinessException

class CategoryAlreadyExistsException : BusinessException(CategoryErrorCode.CATEGORY_ALREADY_EXISTS)
