package com.hoppingmall.common.page

import com.hoppingmall.common.FieldErrorDetail

object PageRequestLimits {
    const val MAX_PAGE_SIZE = 100
    const val PAGE = "page"
    const val SIZE = "size"

    fun requireValid(page: Int, size: Int, pageName: String = PAGE, sizeName: String = SIZE) =
        requireInRange(RangeCheck(pageName, page, 0), RangeCheck(sizeName, size, 1, MAX_PAGE_SIZE))

    fun requireInRange(vararg checks: RangeCheck) {
        val errors = checks.mapNotNull { it.error() }
        if (errors.isNotEmpty()) {
            throw RequestRangeException(errors)
        }
    }
}

data class RangeCheck(
    val name: String,
    val value: Int,
    val min: Int,
    val max: Int = Int.MAX_VALUE
) {
    fun error(): FieldErrorDetail? = when {
        value < min -> FieldErrorDetail(name, "$min 이상이어야 합니다")
        value > max -> FieldErrorDetail(name, "$max 이하여야 합니다")
        else -> null
    }
}

class RequestRangeException(
    val errors: List<FieldErrorDetail>
) : RuntimeException("요청 범위 밖: ${errors.joinToString { it.field }}")
