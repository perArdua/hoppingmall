package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.dto.request.PointPolicyRequest
import com.hoppingmall.payment.point.dto.response.PointPolicyResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface PointPolicyService {
    fun createPolicy(request: PointPolicyRequest): PointPolicyResponse

    fun updatePolicy(policyId: Long, request: PointPolicyRequest): PointPolicyResponse

    fun activatePolicy(policyId: Long): PointPolicyResponse

    fun deactivatePolicy(policyId: Long): PointPolicyResponse

    fun getCurrentPolicy(): PointPolicyResponse?

    fun getPolicyById(policyId: Long): PointPolicyResponse

    fun getPolicies(pageable: Pageable): Slice<PointPolicyResponse>
}
