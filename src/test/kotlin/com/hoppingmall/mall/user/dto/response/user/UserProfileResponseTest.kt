package com.hoppingmall.mall.user.dto.response.user

import com.hoppingmall.mall.global.enums.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("UserProfileResponse")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class UserProfileResponseTest {

    @Nested
    @DisplayName("생성")
    inner class 생성 {

        @Test
        fun `유효한 데이터로 생성 성공`() {
            val response = UserProfileResponse(
                id = 1L,
                email = "test@example.com",
                name = "홍길동",
                role = Role.BUYER.name
            )

            assertEquals(1L, response.id)
            assertEquals("test@example.com", response.email)
            assertEquals("홍길동", response.name)
            assertEquals(Role.BUYER.name, response.role)
        }
    }

    @Nested
    @DisplayName("데이터 접근")
    inner class 데이터_접근 {

        @Test
        fun `모든 필드에 정상적으로 접근 가능`() {
            val response = UserProfileResponse(
                id = 999L,
                email = "user@test.com",
                name = "김철수",
                role = Role.SELLER.name
            )

            assertEquals(999L, response.id)
            assertEquals("user@test.com", response.email)
            assertEquals("김철수", response.name)
            assertEquals(Role.SELLER.name, response.role)
        }
    }
} 