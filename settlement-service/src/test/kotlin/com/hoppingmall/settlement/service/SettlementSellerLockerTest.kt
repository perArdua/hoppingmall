package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.SettlementSellerLock
import com.hoppingmall.settlement.domain.repository.SettlementSellerLockRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus

@ExtendWith(MockitoExtension::class)
@DisplayName("SettlementSellerLocker")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementSellerLockerTest {

    @Mock
    private lateinit var settlementSellerLockRepository: SettlementSellerLockRepository

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var locker: SettlementSellerLocker

    @BeforeEach
    fun setUp() {
        whenever(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        locker = SettlementSellerLocker(settlementSellerLockRepository, transactionManager)
    }

    @Test
    fun 잠금_행이_없으면_새_트랜잭션에서_만든_뒤_그_행을_잠근다() {
        whenever(settlementSellerLockRepository.existsById(SELLER_ID)).thenReturn(false)

        locker.lock(SELLER_ID)

        val saved = argumentCaptor<SettlementSellerLock>()
        verify(settlementSellerLockRepository).saveAndFlush(saved.capture())
        assertThat(saved.firstValue.sellerId).isEqualTo(SELLER_ID)
        verify(settlementSellerLockRepository).findWithLockBySellerId(SELLER_ID)
        val definition = argumentCaptor<TransactionDefinition>()
        verify(transactionManager).getTransaction(definition.capture())
        assertThat(definition.firstValue.propagationBehavior).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    }

    @Test
    fun 잠금_행이_있으면_만들지_않고_잠근다() {
        whenever(settlementSellerLockRepository.existsById(SELLER_ID)).thenReturn(true)

        locker.lock(SELLER_ID)

        verify(settlementSellerLockRepository, never()).saveAndFlush(any<SettlementSellerLock>())
        verify(settlementSellerLockRepository).findWithLockBySellerId(SELLER_ID)
    }

    @Test
    fun 다른_요청이_먼저_잠금_행을_만들어_중복이면_그_행을_잠근다() {
        whenever(settlementSellerLockRepository.existsById(SELLER_ID)).thenReturn(false)
        whenever(settlementSellerLockRepository.saveAndFlush(any<SettlementSellerLock>()))
            .thenThrow(DataIntegrityViolationException("duplicate"))

        assertThatCode { locker.lock(SELLER_ID) }.doesNotThrowAnyException()

        verify(settlementSellerLockRepository).findWithLockBySellerId(SELLER_ID)
    }

    companion object {
        private const val SELLER_ID = 7L
    }
}
