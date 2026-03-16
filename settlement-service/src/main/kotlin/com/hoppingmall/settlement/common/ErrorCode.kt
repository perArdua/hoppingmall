package com.hoppingmall.settlement.common

import org.springframework.http.HttpStatus

interface ErrorCode {
    val code: String
    val message: String
    val status: HttpStatus
}
