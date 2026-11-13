package com.hoppingmall.order.internal

import java.time.LocalDateTime
import java.time.ZoneId

object SettlementPeriod {
    fun toApplicationTime(settlementTime: LocalDateTime, applicationZone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        settlementTime
}
