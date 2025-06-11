package com.hoppingmall.mall.global.vo.password

object DefaultPasswordMaskingStrategy : PasswordMaskingStrategy {
    override fun mask(): String = "******"
}
