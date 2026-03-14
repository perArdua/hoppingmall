package com.hoppingmall.user.domain.repository

import com.hoppingmall.user.domain.MembershipEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MembershipEventLogRepository : JpaRepository<MembershipEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
}
