package com.hoppingmall.global.common.response

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T?, message: String = "성공"): ApiResponse<T> =
            ApiResponse(true, message, data)

        fun <T> failure(message: String = "실패"): ApiResponse<T> =
            ApiResponse(false, message, null)
    }
}
