package com.hoppingmall.mall.category.exception

import com.hoppingmall.mall.category.exception.code.CategoryErrorCode
import com.hoppingmall.mall.global.common.error.exception.BusinessException

class CategoryNotFoundException : BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND)
