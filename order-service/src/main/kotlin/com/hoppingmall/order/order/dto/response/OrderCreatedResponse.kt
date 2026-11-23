package com.hoppingmall.order.order.dto.response

import com.hoppingmall.common.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

fun OrderResponse.toCreatedResponse(): ResponseEntity<ApiResponse<OrderResponse>> =
    ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(this))
