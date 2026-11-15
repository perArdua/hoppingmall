package com.hoppingmall.order.order.dto.request

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("주문 생성 요청의 배송지 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ShippingAddressRequestValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun 배송지가_없는_주문_생성_요청은_검증에_실패한다() {
        val violations = validator.validate(OrderCreateRequest(cartItemIds = listOf(1L)))

        assertThat(violations.map { it.propertyPath.toString() }).containsExactly("shippingAddress")
    }

    @Test
    fun 상세_주소와_배송_메모_없이도_배송지가_있으면_통과한다() {
        val violations = validator.validate(OrderCreateRequest(cartItemIds = listOf(1L), shippingAddress = address()))

        assertThat(violations).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["010-1234-5678", "01012345678", "02-123-4567", "031-1234-5678", "0212345678"])
    fun 국내_전화번호_형식은_통과한다(phone: String) {
        assertThat(validator.validate(address(recipientPhone = phone))).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["010-12-5678", "1012345678", "010-1234-56789", "010 1234 5678", "abc-defg-hijk", "010-1234-5678-9999"])
    fun 전화번호_형식이_틀리면_실패한다(phone: String) {
        assertThat(validator.validate(address(recipientPhone = phone)).map { it.propertyPath.toString() })
            .containsExactly("recipientPhone")
    }

    @ParameterizedTest
    @ValueSource(strings = ["1234", "123456", "12a45", " 12345"])
    fun 우편번호가_숫자_다섯_자리가_아니면_실패한다(zipCode: String) {
        assertThat(validator.validate(address(zipCode = zipCode)).map { it.propertyPath.toString() })
            .containsExactly("zipCode")
    }

    @Test
    fun 받는_사람_이름이나_주소가_비면_실패한다() {
        assertThat(validator.validate(address(recipientName = " ")).map { it.propertyPath.toString() })
            .containsExactly("recipientName")
        assertThat(validator.validate(address(address = "")).map { it.propertyPath.toString() })
            .containsExactly("address")
    }

    @Test
    fun 길이_상한을_넘으면_실패한다() {
        assertThat(validator.validate(address(recipientName = "가".repeat(51)))).hasSize(1)
        assertThat(validator.validate(address(address = "가".repeat(151)))).hasSize(1)
        assertThat(validator.validate(address(addressDetail = "가".repeat(91)))).hasSize(1)
        assertThat(validator.validate(address(deliveryMemo = "가".repeat(201)))).hasSize(1)
        assertThat(
            validator.validate(
                address(
                    recipientName = "가".repeat(50), address = "가".repeat(150),
                    addressDetail = "가".repeat(90), deliveryMemo = "가".repeat(200)
                )
            )
        ).isEmpty()
    }

    @Test
    fun 배송지_요청의_문자열_표현에는_받는_사람_정보가_없다() {
        val text = OrderCreateRequest(cartItemIds = listOf(1L), shippingAddress = address()).toString()

        assertThat(text).doesNotContain("홍길동", "010-1234-5678", "06236", "테헤란로", "101동")
    }

    @Test
    fun 배송지로_바꿀_때_앞뒤_공백을_지우고_빈_선택_항목은_비운다() {
        val shippingAddress = address(recipientName = " 홍길동 ", addressDetail = "  ", deliveryMemo = " 문 앞 ").toShippingAddress()

        assertThat(shippingAddress.recipientName).isEqualTo("홍길동")
        assertThat(shippingAddress.addressDetail).isNull()
        assertThat(shippingAddress.deliveryMemo).isEqualTo("문 앞")
        assertThat(shippingAddress.toString()).doesNotContain("홍길동")
    }

    private fun address(
        recipientName: String = "홍길동",
        recipientPhone: String = "010-1234-5678",
        zipCode: String = "06236",
        address: String = "서울시 강남구 테헤란로 1",
        addressDetail: String? = "101동",
        deliveryMemo: String? = null
    ) = ShippingAddressRequest(
        recipientName = recipientName, recipientPhone = recipientPhone, zipCode = zipCode,
        address = address, addressDetail = addressDetail, deliveryMemo = deliveryMemo
    )
}
