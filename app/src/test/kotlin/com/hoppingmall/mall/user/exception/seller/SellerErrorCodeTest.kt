package com.hoppingmall.mall.user.exception.seller

import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.assertNotNull
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

@DisplayName("SellerErrorCode")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerErrorCodeTest {

    @Nested
    @DisplayName("에러 코드 검증")
    inner class ErrorCodeValidation {
        @Test
        fun 모든_판매자_에러코드는_유효한_코드와_메시지_상태를_가진다() {
            val expected = mapOf(
                SellerErrorCode.ALREADY_APPLIED to Triple("S001", "이미 판매자 신청이 완료된 사용자입니다.", HttpStatus.CONFLICT),
                SellerErrorCode.BUSINESS_NUMBER_DUPLICATED to Triple("S002", "이미 등록된 사업자등록번호입니다.", HttpStatus.CONFLICT),
                SellerErrorCode.INVALID_APPROVAL_STATUS to Triple("S003", "유효하지 않은 판매자 승인 상태입니다.", HttpStatus.BAD_REQUEST),
                SellerErrorCode.SELLER_NOT_FOUND to Triple("S004", "존재하지 않는 판매자입니다.", HttpStatus.NOT_FOUND),
                SellerErrorCode.SELLER_INVALID_APPROVAL_COMMAND to Triple("S005", "지원하지 않는 판매자 승인 상태입니다.", HttpStatus.BAD_REQUEST)
            )

            expected.forEach { (errorCode, expectedValues) ->
                assertEquals(expectedValues.first, errorCode.code)
                assertEquals(expectedValues.second, errorCode.message)
                assertEquals(expectedValues.third, errorCode.status)
            }
        }

        @Test
        fun 모든_에러코드는_ErrorCode_인터페이스를_구현한다() {
            SellerErrorCode.values().forEach { errorCode ->
                assertNotNull(errorCode.code)
                assertNotNull(errorCode.message)
                assertNotNull(errorCode.status)
            }
        }
    }
}
