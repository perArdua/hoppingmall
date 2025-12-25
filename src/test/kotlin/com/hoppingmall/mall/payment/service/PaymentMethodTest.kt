package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.enum.PaymentMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PaymentMethod")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentMethodTest {

    @Nested
    @DisplayName("values")
    inner class Values {
        
        @Test
        fun PaymentMethod의_모든_값들이_올바르게_정의되어_있다() {
            // when
            val values = PaymentMethod.values()
            
            // then
            assertEquals(2, values.size)
            assertTrue(values.contains(PaymentMethod.CREDIT_CARD))
            assertTrue(values.contains(PaymentMethod.BANK_TRANSFER))
        }
    }

    @Nested
    @DisplayName("valueOf")
    inner class ValueOf {
        
        @Test
        fun 문자열로_PaymentMethod_값을_조회하면_올바른_enum_값이_반환된다() {
            // when & then
            assertEquals(PaymentMethod.CREDIT_CARD, PaymentMethod.valueOf("CREDIT_CARD"))
            assertEquals(PaymentMethod.BANK_TRANSFER, PaymentMethod.valueOf("BANK_TRANSFER"))
        }
    }

    @Nested
    @DisplayName("name")
    inner class Name {
        
        @Test
        fun PaymentMethod의_name은_올바른_문자열을_반환한다() {
            // when & then
            assertEquals("CREDIT_CARD", PaymentMethod.CREDIT_CARD.name)
            assertEquals("BANK_TRANSFER", PaymentMethod.BANK_TRANSFER.name)
        }
    }

    @Nested
    @DisplayName("ordinal")
    inner class Ordinal {
        
        @Test
        fun PaymentMethod의_ordinal은_올바른_순서를_반환한다() {
            // when & then
            assertEquals(0, PaymentMethod.CREDIT_CARD.ordinal)
            assertEquals(1, PaymentMethod.BANK_TRANSFER.ordinal)
        }
    }
}
