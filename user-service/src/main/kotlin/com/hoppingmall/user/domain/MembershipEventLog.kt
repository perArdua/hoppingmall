package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.user.domain.enums.MembershipEventType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "membership_event_logs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_id"])],
    indexes = [Index(name = "idx_membership_event_logs_payment_id", columnList = "payment_id")]
)
class MembershipEventLog(
    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: String,

    @Column(nullable = false)
    val paymentId: Long,

    @Column(nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    val type: MembershipEventType? = MembershipEventType.PURCHASE
) : BaseEntity()
