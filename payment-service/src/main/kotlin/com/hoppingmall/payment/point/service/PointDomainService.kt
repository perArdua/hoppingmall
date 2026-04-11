package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class PointDomainService(
    private val pointRepository: PointRepository,
    private val txManager: PlatformTransactionManager
) {

    @Transactional
    fun findOrCreatePoint(userId: Long): Point {
        pointRepository.findByUserIdForUpdate(userId)?.let { return it }

        val newTx = TransactionTemplate(txManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        newTx.execute {
            try {
                pointRepository.save(Point(userId = userId))
            } catch (_: DataIntegrityViolationException) {
            }
        }

        return pointRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("포인트 생성 실패: 사용자 $userId")
    }
}
