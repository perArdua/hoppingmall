package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.dto.request.PointPolicyRequest
import com.hoppingmall.payment.point.dto.response.PointPolicyResponse

interface PointPolicyService {
    fun createPolicy(request: PointPolicyRequest): PointPolicyResponse

    fun updatePolicy(policyId: Long, request: PointPolicyRequest): PointPolicyResponse

    fun activatePolicy(policyId: Long): PointPolicyResponse

    fun deactivatePolicy(policyId: Long): PointPolicyResponse

    fun getCurrentPolicy(): PointPolicyResponse?

    fun getPolicyById(policyId: Long): PointPolicyResponse
}
