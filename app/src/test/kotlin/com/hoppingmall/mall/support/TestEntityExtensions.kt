package com.hoppingmall.mall.support

fun <T : Any> T.withId(id: Long): T {
    var clazz: Class<*>? = this::class.java
    while (clazz != null) {
        try {
            val field = clazz.getDeclaredField("id")
            field.trySetAccessible()
            field.set(this, id)
            return this
        } catch (e: NoSuchFieldException) {
            clazz = clazz.superclass
        }
    }
    throw IllegalStateException("Field 'id' not found in ${this::class.java}")
}
