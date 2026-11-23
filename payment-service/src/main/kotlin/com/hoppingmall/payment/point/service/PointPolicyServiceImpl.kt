package com.hoppingmall.payment.point.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.payment.point.domain.PointPolicy
import com.hoppingmall.payment.point.domain.PointPolicyRepository
import com.hoppingmall.payment.point.dto.request.PointPolicyRequest
import com.hoppingmall.payment.point.dto.response.PointPolicyResponse
import com.hoppingmall.payment.point.exception.PointPolicyConcurrentChangeException
import com.hoppingmall.payment.point.exception.PointPolicyInvalidException
import com.hoppingmall.payment.point.exception.PointPolicyNameDuplicatedException
import com.hoppingmall.payment.point.exception.PointPolicyNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class PointPolicyServiceImpl(
    private val pointPolicyRepository: PointPolicyRepository
) : PointPolicyService {

    @CacheEvict(cacheNames = ["point-policy"], allEntries = true)
    override fun createPolicy(request: PointPolicyRequest): PointPolicyResponse {
        validatePolicyRequest(request)
        validatePolicyNameNotExists(request.policyName)

        val policy = PointPolicy.create(
            policyName = request.policyName,
            earnRate = request.earnRate,
            maxEarnRate = request.maxEarnRate,
            minUseAmount = request.minUseAmount,
            maxUseAmount = request.maxUseAmount,
            description = request.description
        )
        policy.activate()

        val savedPolicy = switchingActivePolicy {
            pointPolicyRepository.deactivateAll()
            pointPolicyRepository.saveAndFlush(policy)
        }
        return PointPolicyResponse.from(savedPolicy)
    }

    @CacheEvict(cacheNames = ["point-policy"], allEntries = true)
    override fun updatePolicy(policyId: Long, request: PointPolicyRequest): PointPolicyResponse {
        validatePolicyRequest(request)

        val policy = pointPolicyRepository.findByIdOrNull(policyId) ?: throw PointPolicyNotFoundException()
        if (policy.policyName != request.policyName) {
            validatePolicyNameNotExists(request.policyName)
        }

        policy.update(
            policyName = request.policyName,
            earnRate = request.earnRate,
            maxEarnRate = request.maxEarnRate,
            minUseAmount = request.minUseAmount,
            maxUseAmount = request.maxUseAmount,
            description = request.description
        )
        return PointPolicyResponse.from(pointPolicyRepository.save(policy))
    }

    @CacheEvict(cacheNames = ["point-policy"], allEntries = true)
    override fun activatePolicy(policyId: Long): PointPolicyResponse {
        val policy = pointPolicyRepository.findByIdOrNull(policyId) ?: throw PointPolicyNotFoundException()

        val activated = switchingActivePolicy {
            pointPolicyRepository.deactivateAllExcept(policyId)
            policy.activate()
            pointPolicyRepository.saveAndFlush(policy)
        }
        return PointPolicyResponse.from(activated)
    }

    @CacheEvict(cacheNames = ["point-policy"], allEntries = true)
    override fun deactivatePolicy(policyId: Long): PointPolicyResponse {
        val policy = pointPolicyRepository.findByIdOrNull(policyId) ?: throw PointPolicyNotFoundException()

        policy.deactivate()
        return PointPolicyResponse.from(pointPolicyRepository.save(policy))
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

    @Transactional(readOnly = true)
    override fun getPolicies(pageable: Pageable): Slice<PointPolicyResponse> {
        return pointPolicyRepository.findAllByOrderByIdDesc(pageable).map { PointPolicyResponse.from(it) }
    }

    private fun validatePolicyRequest(request: PointPolicyRequest) {
        if (request.earnRate <= BigDecimal.ZERO ||
            request.earnRate > request.maxEarnRate ||
            request.minUseAmount > request.maxUseAmount
        ) {
            throw PointPolicyInvalidException()
        }
    }

    private fun validatePolicyNameNotExists(policyName: String) {
        if (pointPolicyRepository.existsByPolicyName(policyName)) {
            throw PointPolicyNameDuplicatedException()
        }
    }

    private fun switchingActivePolicy(action: () -> PointPolicy): PointPolicy =
        try {
            action()
        } catch (ex: PessimisticLockingFailureException) {
            throw PointPolicyConcurrentChangeException()
        } catch (ex: DataIntegrityViolationException) {
            if (ex.mostSpecificCause.message?.contains(PointPolicy.ACTIVE_KEY_CONSTRAINT, ignoreCase = true) == true) {
                throw PointPolicyConcurrentChangeException()
            }
            throw ex
        }
}
