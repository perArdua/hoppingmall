package com.hoppingmall.user.dto.request

import com.hoppingmall.user.common.vo.Email as EmailValue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class SignInRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:Pattern(regexp = EmailValue.FORMAT, message = "이메일 형식이 올바르지 않습니다.")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String
)
