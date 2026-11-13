package com.hoppingmall.user.domain.repository

import com.hoppingmall.user.domain.MembershipEventLog
import com.hoppingmall.user.domain.enums.MembershipEventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MembershipEventLogRepository : JpaRepository<MembershipEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean

    @Query(
        "SELECT COUNT(l) > 0 FROM MembershipEventLog l " +
            "WHERE l.paymentId = :paymentId AND (l.type IS NULL OR l.type = :purchaseType)"
    )
    fun existsPurchaseByPaymentId(
        @Param("paymentId") paymentId: Long,
        @Param("purchaseType") purchaseType: MembershipEventType
    ): Boolean
}
