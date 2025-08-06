package com.hoppingmall.mall.point.controller

import com.hoppingmall.mall.point.dto.request.PointPolicyRequest
import com.hoppingmall.mall.point.dto.response.PointPolicyResponse
import com.hoppingmall.mall.point.service.PointPolicyService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/point-policies")
class PointPolicyController(
    private val pointPolicyService: PointPolicyService
) {
    
    @PostMapping
    fun createPolicy(
        @Valid @RequestBody request: PointPolicyRequest
    ): ResponseEntity<PointPolicyResponse> {
        val policy = pointPolicyService.createPolicy(request)
        return ResponseEntity.ok(policy)
    }
    
    @PutMapping("/{policyId}")
    fun updatePolicy(
        @PathVariable policyId: Long,
        @Valid @RequestBody request: PointPolicyRequest
    ): ResponseEntity<PointPolicyResponse> {
        val policy = pointPolicyService.updatePolicy(policyId, request)
        return ResponseEntity.ok(policy)
    }
    
    @PostMapping("/{policyId}/activate")
    fun activatePolicy(
        @PathVariable policyId: Long
    ): ResponseEntity<PointPolicyResponse> {
        val policy = pointPolicyService.activatePolicy(policyId)
        return ResponseEntity.ok(policy)
    }
    
    @PostMapping("/{policyId}/deactivate")
    fun deactivatePolicy(
        @PathVariable policyId: Long
    ): ResponseEntity<PointPolicyResponse> {
        val policy = pointPolicyService.deactivatePolicy(policyId)
        return ResponseEntity.ok(policy)
    }
    
    @GetMapping("/current")
    fun getCurrentPolicy(): ResponseEntity<PointPolicyResponse?> {
        val policy = pointPolicyService.getCurrentPolicy()
        return ResponseEntity.ok(policy)
    }
    
    @GetMapping("/{policyId}")
    fun getPolicyById(
        @PathVariable policyId: Long
    ): ResponseEntity<PointPolicyResponse> {
        val policy = pointPolicyService.getPolicyById(policyId)
        return ResponseEntity.ok(policy)
    }
} 