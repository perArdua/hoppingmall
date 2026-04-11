package com.hoppingmall.payment.point.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.point.domain.PointPolicy
import com.hoppingmall.payment.point.domain.PointPolicyRepository
import com.hoppingmall.payment.point.dto.request.PointPolicyRequest
import com.hoppingmall.payment.point.exception.PointPolicyNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("PointPolicyServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PointPolicyServiceImplTest {

    @Mock
    private lateinit var pointPolicyRepository: PointPolicyRepository

    @InjectMocks
    private lateinit var pointPolicyService: PointPolicyServiceImpl

    private fun setBaseEntityFields(entity: Any, id: Long = 1L) {
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        if (idField.get(entity) == null) idField.set(entity, id)
    }

    private fun mockSaveWithIdInjection() {
        doAnswer {
            val p = it.arguments[0] as PointPolicy
            setBaseEntityFields(p)
            p
        }.whenever(pointPolicyRepository).save(any())
    }

    private fun createRequest(): PointPolicyRequest {
        return PointPolicyRequest(
            policyName = "기본 정책",
            earnRate = BigDecimal("0.01"),
            maxEarnRate = BigDecimal("0.05"),
            minUseAmount = BigDecimal("100"),
            maxUseAmount = BigDecimal("10000"),
            description = null
        )
    }

    private fun createPolicy(id: Long = 1L): PointPolicy {
        val policy = PointPolicy.create(
            policyName = "기본 정책",
            earnRate = BigDecimal("0.01"),
            maxEarnRate = BigDecimal("0.05"),
            minUseAmount = BigDecimal("100"),
            maxUseAmount = BigDecimal("10000")
        )
        setBaseEntityFields(policy, id)
        return policy
    }

    @Test
    fun 정책을_생성한다() {
        whenever(pointPolicyRepository.existsByPolicyName("기본 정책")).thenReturn(false)
        whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(null)
        mockSaveWithIdInjection()

        val result = pointPolicyService.createPolicy(createRequest())

        assertThat(result).isNotNull
    }

    @Test
    fun 현재_활성_정책을_조회한다() {
        val policy = createPolicy()
        val activated = policy.activate()
        setBaseEntityFields(activated)
        whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(activated)

        val result = pointPolicyService.getCurrentPolicy()

        assertThat(result).isNotNull
    }

    @Test
    fun 활성_정책이_없으면_null을_반환한다() {
        whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(null)

        val result = pointPolicyService.getCurrentPolicy()

        assertThat(result).isNull()
    }

    @Test
    fun 정책을_활성화한다() {
        val policy = createPolicy()
        whenever(pointPolicyRepository.findById(1L)).thenReturn(Optional.of(policy))
        whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(null)
        mockSaveWithIdInjection()

        val result = pointPolicyService.activatePolicy(1L)

        assertThat(result).isNotNull
    }

    @Test
    fun 정책을_비활성화한다() {
        val policy = createPolicy()
        val activated = policy.activate()
        setBaseEntityFields(activated)
        whenever(pointPolicyRepository.findById(1L)).thenReturn(Optional.of(activated))
        mockSaveWithIdInjection()

        val result = pointPolicyService.deactivatePolicy(1L)

        assertThat(result).isNotNull
    }

    @Test
    fun 존재하지_않는_정책_조회_시_예외가_발생한다() {
        whenever(pointPolicyRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { pointPolicyService.getPolicyById(999L) }
            .isInstanceOf(PointPolicyNotFoundException::class.java)
    }

    @Test
    fun 정책을_업데이트한다() {
        val policy = createPolicy()
        whenever(pointPolicyRepository.findById(1L)).thenReturn(Optional.of(policy))
        mockSaveWithIdInjection()

        val request = PointPolicyRequest(
            policyName = "수정 정책",
            earnRate = BigDecimal("0.02"),
            maxEarnRate = BigDecimal("0.10"),
            minUseAmount = BigDecimal("200"),
            maxUseAmount = BigDecimal("50000")
        )

        val result = pointPolicyService.updatePolicy(1L, request)

        assertThat(result).isNotNull
    }

    @Test
    fun 존재하지_않는_정책_업데이트_시_예외가_발생한다() {
        whenever(pointPolicyRepository.findById(999L)).thenReturn(Optional.empty())

        val request = PointPolicyRequest(
            policyName = "수정 정책",
            earnRate = BigDecimal("0.02"),
            maxEarnRate = BigDecimal("0.10"),
            minUseAmount = BigDecimal("200"),
            maxUseAmount = BigDecimal("50000")
        )

        assertThatThrownBy { pointPolicyService.updatePolicy(999L, request) }
            .isInstanceOf(PointPolicyNotFoundException::class.java)
    }

    @Test
    fun 존재하지_않는_정책_활성화_시_예외가_발생한다() {
        whenever(pointPolicyRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { pointPolicyService.activatePolicy(999L) }
            .isInstanceOf(PointPolicyNotFoundException::class.java)
    }

    @Test
    fun 존재하지_않는_정책_비활성화_시_예외가_발생한다() {
        whenever(pointPolicyRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { pointPolicyService.deactivatePolicy(999L) }
            .isInstanceOf(PointPolicyNotFoundException::class.java)
    }
}
