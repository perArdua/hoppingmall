package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.EnumNameConverter
import com.hoppingmall.user.domain.enums.MembershipEventType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

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

    @Convert(converter = MembershipEventTypeConverter::class)
    @Column(length = 32)
    val type: MembershipEventType? = MembershipEventType.PURCHASE
) : BaseEntity()

class MembershipEventTypeConverter : EnumNameConverter<MembershipEventType>(MembershipEventType::class.java)
