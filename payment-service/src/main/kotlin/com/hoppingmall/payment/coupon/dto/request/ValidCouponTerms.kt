package com.hoppingmall.payment.coupon.dto.request

import com.hoppingmall.payment.coupon.enum.DiscountType
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.math.BigDecimal
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [CouponTermsValidator::class])
annotation class ValidCouponTerms(
    val message: String = "쿠폰 조건이 올바르지 않습니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class CouponTermsValidator : ConstraintValidator<ValidCouponTerms, CouponCreateRequest> {

    override fun isValid(value: CouponCreateRequest?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        context.disableDefaultConstraintViolation()
        var valid = true
        if (value.discountType == DiscountType.PERCENTAGE && value.discountValue > MAX_PERCENTAGE) {
            context.buildConstraintViolationWithTemplate("비율 할인은 100 이하여야 합니다.")
                .addPropertyNode("discountValue")
                .addConstraintViolation()
            valid = false
        }
        if (!value.validFrom.isBefore(value.validTo)) {
            context.buildConstraintViolationWithTemplate("유효 종료일은 유효 시작일보다 뒤여야 합니다.")
                .addPropertyNode("validTo")
                .addConstraintViolation()
            valid = false
        }
        return valid
    }

    private companion object {
        val MAX_PERCENTAGE: BigDecimal = BigDecimal(100)
    }
}
