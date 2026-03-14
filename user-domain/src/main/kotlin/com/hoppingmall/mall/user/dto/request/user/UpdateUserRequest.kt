package com.hoppingmall.mall.user.dto.request.user

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateUserRequest(
    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(min = 1, max = 50, message = "이름은 1-50자 사이여야 합니다")
    val name: String,
    
    val password: String? = null
) 