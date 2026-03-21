package com.hoppingmall.common

data class ApiResponse<T>(
    val code: String,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> =
            ApiResponse(code = "SUCCESS", message = "성공", data = data)

        fun <T> failure(message: String): ApiResponse<T> =
            ApiResponse(code = "FAIL", message = message, data = null)

        fun <T> failure(errorCode: ErrorCode): ApiResponse<T> =
            ApiResponse(code = errorCode.code, message = errorCode.message, data = null)
    }
}
