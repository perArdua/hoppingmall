package com.hoppingmall.payment.point.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.payment.point.dto.request.PointPolicyRequest
import com.hoppingmall.payment.point.dto.response.PointPolicyResponse
import com.hoppingmall.payment.point.service.PointPolicyService
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/point-policies")
@Tag(name = "포인트 정책")
class PointPolicyController(
    private val pointPolicyService: PointPolicyService
) {

    @PostMapping
    fun createPolicy(
        @Valid @RequestBody request: PointPolicyRequest
    ): ApiResponse<PointPolicyResponse> {
        return ApiResponse.success(pointPolicyService.createPolicy(request))
    }

    @PutMapping("/{policyId}")
    fun updatePolicy(
        @PathVariable policyId: Long,
        @Valid @RequestBody request: PointPolicyRequest
    ): ApiResponse<PointPolicyResponse> {
        return ApiResponse.success(pointPolicyService.updatePolicy(policyId, request))
    }

    @PostMapping("/{policyId}/activate")
    fun activatePolicy(
        @PathVariable policyId: Long
    ): ApiResponse<PointPolicyResponse> {
        return ApiResponse.success(pointPolicyService.activatePolicy(policyId))
    }

    @PostMapping("/{policyId}/deactivate")
    fun deactivatePolicy(
        @PathVariable policyId: Long
    ): ApiResponse<PointPolicyResponse> {
        return ApiResponse.success(pointPolicyService.deactivatePolicy(policyId))
    }

    @GetMapping("/current")
    fun getCurrentPolicy(): ApiResponse<PointPolicyResponse?> {
        return ApiResponse.success(pointPolicyService.getCurrentPolicy())
    }

    @GetMapping("/{policyId}")
    fun getPolicyById(
        @PathVariable policyId: Long
    ): ApiResponse<PointPolicyResponse> {
        return ApiResponse.success(pointPolicyService.getPolicyById(policyId))
    }
}
