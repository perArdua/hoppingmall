package com.hoppingmall.settlement.domain

import com.hoppingmall.settlement.exception.SettlementInvalidStatusException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("정산 이월 기록")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementCarryOverTest {

    @Test
    fun 이월은_한_정산에서만_뺀다() {
        val carryOver = SettlementCarryOver.create(sellerId = 1L, sourceSettlementId = 10L, amount = BigDecimal("19000"))

        carryOver.applyTo(11L)

        assertThat(carryOver.appliedSettlementId).isEqualTo(11L)
        assertThatThrownBy { carryOver.applyTo(12L) }.isInstanceOf(SettlementInvalidStatusException::class.java)
        assertThat(carryOver.appliedSettlementId).isEqualTo(11L)
    }
}
