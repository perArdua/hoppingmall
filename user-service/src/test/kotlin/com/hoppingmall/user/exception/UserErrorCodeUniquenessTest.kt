package com.hoppingmall.user.exception

import com.hoppingmall.common.CommonErrorCode
import com.hoppingmall.common.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter

@DisplayName("user 오류 코드의 유일성과 접두사")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserErrorCodeUniquenessTest {

    private val userCodes: List<ErrorCode> by lazy {
        val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
            addIncludeFilter(AssignableTypeFilter(ErrorCode::class.java))
        }
        scanner.findCandidateComponents("com.hoppingmall.user")
            .map { Class.forName(it.beanClassName) }
            .filter { it.isEnum }
            .flatMap { it.enumConstants.map { constant -> constant as ErrorCode } }
    }

    @Test
    fun user_오류_코드를_찾는다() {
        assertThat(userCodes.map { it.javaClass.simpleName }.distinct())
            .contains("AuthErrorCode", "SellerErrorCode", "PasswordErrorCode", "MembershipErrorCode", "UserErrorCode")
    }

    @Test
    fun user_오류_코드는_공통_오류_코드와_겹치지_않는다() {
        val commonCodes = CommonErrorCode.entries.map { it.code }.toSet()

        assertThat(userCodes.map { it.code }.filter { it in commonCodes }).isEmpty()
    }

    @Test
    fun user_오류_코드는_서로_겹치지_않는다() {
        val duplicated = userCodes.groupBy { it.code }.filterValues { it.size > 1 }.keys

        assertThat(duplicated).isEmpty()
    }

    @Test
    fun user_오류_코드는_user_전용_접두사와_세_자리_번호다() {
        val outside = userCodes.map { it.code }.filterNot { USER_CODE.matches(it) }

        assertThat(outside).isEmpty()
    }

    private companion object {
        val USER_CODE = Regex("^(AUTH|SEL|PWD|MBR|U)\\d{3}$")
    }
}
