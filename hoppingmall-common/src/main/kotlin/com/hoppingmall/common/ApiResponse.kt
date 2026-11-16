package com.hoppingmall.common

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiResponse<T>(
    val code: String,
    val message: String,
    val data: T? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val errors: List<FieldErrorDetail>? = null
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> =
            ApiResponse(code = "SUCCESS", message = "성공", data = data)

        fun <T> failure(errorCode: ErrorCode): ApiResponse<T> =
            ApiResponse(code = errorCode.code, message = errorCode.message, data = null)
    }
}

data class FieldErrorDetail(
    val field: String,
    val reason: String
)
