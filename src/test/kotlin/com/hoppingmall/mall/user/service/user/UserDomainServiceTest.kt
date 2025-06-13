package com.hoppingmall.mall.user.domain.service

import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.policy.PasswordPolicy
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.exception.user.UserAlreadyExistsException
import com.hoppingmall.mall.user.service.user.UserDomainServiceImpl
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserDomainServiceTest {

    private val userRepository: UserRepository = mock()
    private val passwordPolicy: PasswordPolicy = mock()
    private val userDomainService = UserDomainServiceImpl(userRepository, passwordPolicy)

    @Test
    fun `이미 존재하는 이메일이면 예외가 발생한다`() {
        val email = Email("dup@example.com")
        whenever(userRepository.existsByEmail(email)).thenReturn(true)

        assertThrows(UserAlreadyExistsException::class.java) {
            userDomainService.validateNewUser(email, "secure123")
        }
    }

    @Test
    fun `정상 이메일이면 비밀번호 정책 검증이 호출된다`() {
        val email = Email("ok@example.com")
        whenever(userRepository.existsByEmail(email)).thenReturn(false)

        userDomainService.validateNewUser(email, "secure123")

        verify(passwordPolicy).validate("secure123")
    }
}
