package com.hoppingmall.mall.global.vo.password.strategy

object DefaultPasswordMaskingStrategy : PasswordMaskingStrategy {
    override fun mask(): String = "******"
}
