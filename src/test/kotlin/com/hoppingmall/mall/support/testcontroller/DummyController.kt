package com.hoppingmall.mall.support.testcontroller

import com.hoppingmall.mall.global.common.error.code.CommonErrorCode
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.user.exception.user.UserErrorCode
import com.hoppingmall.mall.global.common.error.exception.BusinessException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.*

data class DummyRequest(
    @field:NotBlank(message = "name은 필수입니다.")
    val name: String?
)

@RestController
@RequestMapping("/test-exception")
class DummyController {

    @GetMapping("/business")
    fun throwBusiness(): ApiResponse<Unit> {
        throw DummyBusinessException()
    }

    @GetMapping("/runtime")
    fun throwRuntime(): ApiResponse<Unit> {
        throw IllegalStateException("알 수 없는 서버 오류")
    }

    @GetMapping("/validation")
    fun triggerGetValidation(@Valid request: DummyRequest): ApiResponse<Unit> {
        return ApiResponse.success(Unit)
    }

    @GetMapping("/common-error")
    fun throwCommonError(): String {
        throw BusinessException(CommonErrorCode.INVALID_INPUT)
    }

    class DummyBusinessException : BusinessException(UserErrorCode.USER_ALREADY_EXISTS)
}
