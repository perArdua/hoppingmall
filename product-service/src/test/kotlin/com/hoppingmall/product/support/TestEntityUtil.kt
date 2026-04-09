package com.hoppingmall.product.support

import com.hoppingmall.common.BaseEntity

fun <T : BaseEntity> T.withId(id: Long): T {
    val field = BaseEntity::class.java.getDeclaredField("id")
    field.isAccessible = true
    field.set(this, id)
    return this
}
