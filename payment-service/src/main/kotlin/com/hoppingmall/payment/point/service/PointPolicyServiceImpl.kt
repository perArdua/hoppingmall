package com.hoppingmall.payment.point.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.payment.point.domain.PointPolicy
import com.hoppingmall.payment.point.domain.PointPolicyRepository
import com.hoppingmall.payment.point.dto.request.PointPolicyRequest
import com.hoppingmall.payment.point.dto.response.PointPolicyResponse
import com.hoppingmall.payment.point.exception.PointPolicyNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PointPolicyServiceImpl(
    private val pointPolicyRepository: PointPolicyRepository
) : PointPolicyService {

    @CacheEvict(cacheNames = ["point-policy"], allEntries = true)
    override fun createPolicy(request: PointPolicyRequest): PointPolicyResponse {
        validatePolicyRequest(request)
        validatePolicyNameNotExists(request.policyName)

        deactivateAllPolicies()

        val policy = PointPolicy.create(
            policyName = request.policyName,
            earnRate = request.earnRate,
            maxEarnRate = request.maxEarnRate,
            minUseAmount = request.minUseAmount,
            maxUseAmount = request.maxUseAmount,
            description = request.description
        ).activate()

        val savedPolicy = pointPolicyRepository.save(policy)
        return PointPolicyResponse.from(savedPolicy)
    }

    @CacheEvict(cacheNames = ["point-policy"], allEntries = true)
    override fun updatePolicy(policyId: Long, request: PointPolicyRequest): PointPolicyResponse {
        validatePolicyRequest(request)

        val policy = pointPolicyRepository.findByIdOrNull(policyId) ?: throw PointPolicyNotFoundException() 

        val updatedPolicy = policy.update(
            policyName = request.policyName,
            earnRate = request.earnRate,
            maxEarnRate = request.maxEarnRate,
            minUseAmount = request.minUseAmount,
            maxUseAmount = request.maxUseAmount,
            description = request.description
        )

        val savedPolicy = pointPolicyRepository.save(updatedPolicy)
        return PointPolicyResponse.from(savedPolicy)
    }

    @CacheEvict(cacheNames = ["point-policy"], allEntries = true)
    override fun activatePolicy(policyId: Long): PointPolicyResponse {
        val policy = pointPolicyRepository.findByIdOrNull(policyId) ?: throw PointPolicyNotFoundException() 

        deactivateAllPolicies()

        val activatedPolicy = policy.activate()
        val savedPolicy = pointPolicyRepository.save(activatedPolicy)
        return PointPolicyResponse.from(savedPolicy)
    }

    @CacheEvict(cacheNames = ["point-policy"], allEntries = true)
    override fun deactivatePolicy(policyId: Long): PointPolicyResponse {
        val policy = pointPolicyRepository.findByIdOrNull(policyId) ?: throw PointPolicyNotFoundException() 

        val deactivatedPolicy = policy.deactivate()
        val savedPolicy = pointPolicyRepository.save(deactivatedPolicy)
        return PointPolicyResponse.from(savedPolicy)
    }

    @Cacheable(cacheNames = ["point-policy"], key = "'current'", sync = true)
    override fun getCurrentPolicy(): PointPolicyResponse? {
        val policy = pointPolicyRepository.findByIsActiveTrue()
        return policy?.let { PointPolicyResponse.from(it) }
    }

    override fun getPolicyById(policyId: Long): PointPolicyResponse {
        val policy = pointPolicyRepository.findByIdOrNull(policyId) ?: throw PointPolicyNotFoundException() 
        return PointPolicyResponse.from(policy)
    }

    private fun validatePolicyRequest(request: PointPolicyRequest) {
        if (request.earnRate > request.maxEarnRate) {
            throw IllegalArgumentException("적립률은 최대 적립률을 초과할 수 없습니다")
        }
        if (request.minUseAmount > request.maxUseAmount) {
            throw IllegalArgumentException("최소 사용 금액은 최대 사용 금액을 초과할 수 없습니다")
        }
    }

    private fun validatePolicyNameNotExists(policyName: String) {
        if (pointPolicyRepository.existsByPolicyName(policyName)) {
            throw IllegalArgumentException("이미 존재하는 정책 이름입니다")
        }
    }

    private fun deactivateAllPolicies() {
        val activePolicy = pointPolicyRepository.findByIsActiveTrue()
        activePolicy?.let {
            val deactivatedPolicy = it.deactivate()
            pointPolicyRepository.save(deactivatedPolicy)
        }
    }
}
