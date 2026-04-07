package com.hoppingmall.user.support

import org.springframework.test.util.ReflectionTestUtils

fun <T : Any> T.withId(id: Long): T {
    ReflectionTestUtils.setField(this, "id", id)
    return this
}
