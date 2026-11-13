package com.hoppingmall.product.internal.service

import com.hoppingmall.product.internal.domain.AppliedRequestKey
import com.hoppingmall.product.internal.domain.repository.AppliedRequestKeyRepository
import com.hoppingmall.product.internal.enums.AppliedRequestOperation
import com.hoppingmall.product.internal.exception.InvalidRequestKeyException
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
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus

@DisplayName("AppliedRequestKeyGuard")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class AppliedRequestKeyGuardTest {

    @Mock
    private lateinit var appliedRequestKeyRepository: AppliedRequestKeyRepository

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @InjectMocks
    private lateinit var guard: AppliedRequestKeyGuard

    private var applied = 0

    @BeforeEach
    fun setUp() {
        lenient().`when`(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
    }

    @Test
    fun 처음_보는_키면_키를_남기고_처리한다() {
        whenever(appliedRequestKeyRepository.existsByOperationAndRequestKey(OPERATION.name, KEY)).thenReturn(false)

        guard.applyOnce(OPERATION, KEY) { applied++ }

        assertThat(applied).isEqualTo(1)
        verify(appliedRequestKeyRepository).saveAndFlush(check<AppliedRequestKey> {
            assertThat(it.operation).isEqualTo(OPERATION.name)
            assertThat(it.requestKey).isEqualTo(KEY)
        })
    }

    @Test
    fun 이미_처리한_키면_처리하지_않고_성공으로_끝낸다() {
        whenever(appliedRequestKeyRepository.existsByOperationAndRequestKey(OPERATION.name, KEY)).thenReturn(true)

        guard.applyOnce(OPERATION, KEY) { applied++ }

        assertThat(applied).isZero()
        verify(appliedRequestKeyRepository, never()).saveAndFlush(any<AppliedRequestKey>())
    }

    @Test
    fun 키를_남기다_유니크_충돌이_나도_다른_요청이_처리한_키면_성공으로_끝낸다() {
        whenever(appliedRequestKeyRepository.existsByOperationAndRequestKey(OPERATION.name, KEY))
            .thenReturn(false)
            .thenReturn(true)
        whenever(appliedRequestKeyRepository.saveAndFlush(any<AppliedRequestKey>()))
            .thenThrow(DataIntegrityViolationException("duplicate"))

        guard.applyOnce(OPERATION, KEY) { applied++ }

        assertThat(applied).isZero()
    }

    @Test
    fun 무결성_위반인데_처리된_키가_없으면_예외를_그대로_던진다() {
        whenever(appliedRequestKeyRepository.existsByOperationAndRequestKey(OPERATION.name, KEY)).thenReturn(false)

        assertThatThrownBy {
            guard.applyOnce(OPERATION, KEY) { throw DataIntegrityViolationException("other") }
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun 빈_키는_거절하고_처리하지_않는다() {
        assertThatThrownBy { guard.applyOnce(OPERATION, " ") { applied++ } }
            .isInstanceOf(InvalidRequestKeyException::class.java)

        assertThat(applied).isZero()
    }

    companion object {
        private val OPERATION = AppliedRequestOperation.REFUND_STATISTICS
        private const val KEY = "refund-1-item-2-stat"
    }
}
