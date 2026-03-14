package com.hoppingmall.mall.user.domain.service

import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.policy.PasswordPolicy
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.exception.user.UserAlreadyExistsException
import com.hoppingmall.mall.user.service.user.UserDomainServiceImpl
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("UserDomainService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserDomainServiceTest {

    private val userRepository: UserRepository = mock()
    private val passwordPolicy: PasswordPolicy = mock()
    private val userDomainService = UserDomainServiceImpl(userRepository, passwordPolicy)

    @Nested
    @DisplayName("validateNewUser")
    inner class ValidateNewUser {
        @Test
        fun 이미_존재하는_이메일이면_예외가_발생한다() {
            val email = Email("dup@example.com")
            whenever(userRepository.existsByEmail(email)).thenReturn(true)

            assertThrows(UserAlreadyExistsException::class.java) {
                userDomainService.validateNewUser(email, "secure123")
            }
        }

        @Test
        fun 정상_이메일이면_비밀번호_정책_검증이_호출된다() {
            val email = Email("ok@example.com")
            whenever(userRepository.existsByEmail(email)).thenReturn(false)

            userDomainService.validateNewUser(email, "secure123")

            verify(passwordPolicy).validate("secure123")
        }
    }
}
