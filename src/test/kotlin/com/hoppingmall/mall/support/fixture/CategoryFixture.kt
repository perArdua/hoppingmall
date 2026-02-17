package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.category.domain.Category

fun Category.Companion.fixture(
    name: String = "전자제품",
    parentCategoryId: Long? = null,
    depth: Int = 0
): Category {
    return Category.create(name = name, parentCategoryId = parentCategoryId, depth = depth)
}
