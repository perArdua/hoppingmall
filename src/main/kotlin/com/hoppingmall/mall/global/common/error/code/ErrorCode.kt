package com.hoppingmall.mall.global.common.error.code

import org.springframework.http.HttpStatus

interface ErrorCode {
    val code: String
    val message: String
    val status: HttpStatus
}
