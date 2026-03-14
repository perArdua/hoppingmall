package com.hoppingmall.product.common.cache

import java.io.Serializable

data class NotFoundMarker(
    val reason: String = "NOT_FOUND"
) : Serializable {
    companion object {
        val INSTANCE = NotFoundMarker()

        fun isNotFound(value: Any?): Boolean = value is NotFoundMarker
    }
}
