package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.user.domain.enums.MembershipEventType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "membership_event_logs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_id"])]
)
class MembershipEventLog(
    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: String,

    @Column(nullable = false)
    val paymentId: Long,

    @Column(nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column
    val type: MembershipEventType? = MembershipEventType.PURCHASE
) : BaseEntity()
