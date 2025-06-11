package com.hoppingmall.mall.global.vo.password

fun interface PasswordMaskingStrategy {
    fun mask(): String
}
