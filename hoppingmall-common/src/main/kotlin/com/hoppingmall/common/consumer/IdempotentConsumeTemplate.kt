package com.hoppingmall.common.consumer

import org.slf4j.Logger
import org.springframework.dao.DataIntegrityViolationException

inline fun executeIdempotently(
    eventId: String,
    eventDescription: String,
    logger: Logger,
    existsCheck: () -> Boolean,
    action: () -> Unit
) {
    try {
        if (existsCheck()) {
            logger.info("이미 처리된 {} 이벤트: eventId={}", eventDescription, eventId)
            return
        }
        action()
    } catch (e: DataIntegrityViolationException) {
        val processed = try {
            existsCheck()
        } catch (recheckFailure: Exception) {
            e.addSuppressed(recheckFailure)
            false
        }
        if (!processed) {
            logger.error("{} 처리 중 처리 기록이 아닌 무결성 위반: eventId={}, 오류={}", eventDescription, eventId, e.message)
            throw e
        }
        logger.info("이미 처리된 {} 이벤트: eventId={}", eventDescription, eventId)
    } catch (e: Exception) {
        logger.error("{} 처리 실패: eventId={}, 오류={}", eventDescription, eventId, e.message)
        throw e
    }
}
