package com.hoppingmall.product.category.exception

import com.hoppingmall.product.category.exception.code.CategoryErrorCode
import com.hoppingmall.common.BusinessException

class CategoryHasChildrenException : BusinessException(CategoryErrorCode.CATEGORY_HAS_CHILDREN)
