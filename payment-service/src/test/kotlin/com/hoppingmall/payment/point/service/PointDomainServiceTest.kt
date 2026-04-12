package com.hoppingmall.payment.point.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
@DisplayName("PointDomainService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PointDomainServiceTest {

    @Mock
    private lateinit var pointRepository: PointRepository

    @Mock
    private lateinit var txManager: PlatformTransactionManager

    @InjectMocks
    private lateinit var pointDomainService: PointDomainService

    @Test
    fun 기존_포인트가_있으면_조회하여_반환한다() {
        val existing = Point(userId = 1L, balance = BigDecimal("1000"))
        whenever(pointRepository.findByUserId(1L)).thenReturn(existing)
        whenever(pointRepository.findByUserIdForUpdate(1L)).thenReturn(existing)

        val result = pointDomainService.findOrCreatePoint(1L)

        assertThat(result.userId).isEqualTo(1L)
        assertThat(result.balance).isEqualByComparingTo(BigDecimal("1000"))
        verify(pointRepository, never()).save(any())
    }

    @Test
    fun 포인트가_없으면_새로_생성한다() {
        val newPoint = Point(userId = 1L)
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(newPoint, 1L)
        whenever(pointRepository.findByUserId(1L)).thenReturn(null)
        whenever(pointRepository.findByUserIdForUpdate(1L)).thenReturn(newPoint)
        whenever(txManager.getTransaction(any())).thenReturn(mock())
        doAnswer { newPoint }.whenever(pointRepository).save(any())

        val result = pointDomainService.findOrCreatePoint(1L)

        assertThat(result.userId).isEqualTo(1L)
        verify(pointRepository).save(any())
    }

    @Test
    fun 동시_생성_충돌_시_재조회하여_반환한다() {
        val existing = Point(userId = 1L, balance = BigDecimal.ZERO)
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(existing, 1L)
        whenever(pointRepository.findByUserId(1L)).thenReturn(null)
        whenever(pointRepository.findByUserIdForUpdate(1L)).thenReturn(existing)
        whenever(txManager.getTransaction(any())).thenReturn(mock())
        doThrow(DataIntegrityViolationException("duplicate")).whenever(pointRepository).save(any())

        val result = pointDomainService.findOrCreatePoint(1L)

        assertThat(result.userId).isEqualTo(1L)
    }

    @Test
    fun 동시_생성_충돌_후에도_조회_실패하면_예외가_발생한다() {
        whenever(pointRepository.findByUserId(1L)).thenReturn(null)
        whenever(pointRepository.findByUserIdForUpdate(1L)).thenReturn(null)
        whenever(txManager.getTransaction(any())).thenReturn(mock())
        doThrow(DataIntegrityViolationException("duplicate")).whenever(pointRepository).save(any())

        assertThatThrownBy { pointDomainService.findOrCreatePoint(1L) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
