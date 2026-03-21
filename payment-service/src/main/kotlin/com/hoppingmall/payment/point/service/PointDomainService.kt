package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PointDomainService(
    private val pointRepository: PointRepository
) {

    @Transactional
    fun findOrCreatePoint(userId: Long): Point {
        return try {
            pointRepository.findByUserIdForUpdate(userId)
                ?: run {
                    val newPoint = Point(userId = userId)
                    pointRepository.save(newPoint)
                }
        } catch (e: DataIntegrityViolationException) {
            pointRepository.findByUserIdForUpdate(userId)
                ?: throw IllegalStateException("포인트 생성 실패: 사용자 $userId")
        }
    }
}
