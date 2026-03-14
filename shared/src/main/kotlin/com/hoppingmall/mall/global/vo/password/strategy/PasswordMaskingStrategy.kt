package com.hoppingmall.mall.global.vo.password.strategy

fun interface PasswordMaskingStrategy {
    fun mask(): String
}
