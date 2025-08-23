package com.hoppingmall.mall.payment.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PaymentType")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentTypeTest {

    @Nested
    @DisplayName("values")
    inner class Values {
        
        @Test
        fun PaymentType의_모든_값들이_올바르게_정의되어_있다() {
            // when
            val values = PaymentType.values()
            
            // then
            assertEquals(3, values.size)
            assertTrue(values.contains(PaymentType.CREDIT_CARD))
            assertTrue(values.contains(PaymentType.BANK_TRANSFER))
            assertTrue(values.contains(PaymentType.VIRTUAL_ACCOUNT))
        }
    }

    @Nested
    @DisplayName("valueOf")
    inner class ValueOf {
        
        @Test
        fun 문자열로_PaymentType_값을_조회하면_올바른_enum_값이_반환된다() {
            // when & then
            assertEquals(PaymentType.CREDIT_CARD, PaymentType.valueOf("CREDIT_CARD"))
            assertEquals(PaymentType.BANK_TRANSFER, PaymentType.valueOf("BANK_TRANSFER"))
            assertEquals(PaymentType.VIRTUAL_ACCOUNT, PaymentType.valueOf("VIRTUAL_ACCOUNT"))
        }
    }

    @Nested
    @DisplayName("name")
    inner class Name {
        
        @Test
        fun PaymentType의_name은_올바른_문자열을_반환한다() {
            // when & then
            assertEquals("CREDIT_CARD", PaymentType.CREDIT_CARD.name)
            assertEquals("BANK_TRANSFER", PaymentType.BANK_TRANSFER.name)
            assertEquals("VIRTUAL_ACCOUNT", PaymentType.VIRTUAL_ACCOUNT.name)
        }
    }

    @Nested
    @DisplayName("ordinal")
    inner class Ordinal {
        
        @Test
        fun PaymentType의_ordinal은_올바른_순서를_반환한다() {
            // when & then
            assertEquals(0, PaymentType.CREDIT_CARD.ordinal)
            assertEquals(1, PaymentType.BANK_TRANSFER.ordinal)
            assertEquals(2, PaymentType.VIRTUAL_ACCOUNT.ordinal)
        }
    }
}