package com.hoppingmall.order.internal

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * settlement가 넘기는 정산 기간 시각의 뜻.
 *
 * - 값은 오프셋 없는 한국 시각(Asia/Seoul) 벽시계다. 하루는 `[그날 00:00, 다음 날 00:00)`으로 넘어온다.
 * - order는 끝을 넣지 않는다(`>= startDate AND < endDate`). 맞닿은 두 날에 같은 행이 잡히지 않는다.
 * - order의 시각 열은 앱이 JVM 시간대로 쓴 값이라, 비교 전에 JVM 시간대 시각으로 바꾼다.
 */
object SettlementPeriod {
    val SETTLEMENT_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    fun toApplicationTime(settlementTime: LocalDateTime, applicationZone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        settlementTime.atZone(SETTLEMENT_ZONE).withZoneSameInstant(applicationZone).toLocalDateTime()
}
