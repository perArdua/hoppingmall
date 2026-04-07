package com.hoppingmall.user.service

import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.PasswordPolicy
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.exception.user.UserAlreadyExistsException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("UserDomainServiceImpl 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserDomainServiceImplTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordPolicy: PasswordPolicy

    @InjectMocks
    private lateinit var userDomainService: UserDomainServiceImpl

    @Test
    fun 이미_존재하는_이메일이면_예외가_발생한다() {
        val email = Email("duplicate@example.com")
        whenever(userRepository.existsByEmail(email)).thenReturn(true)

        assertThrows<UserAlreadyExistsException> {
            userDomainService.validateNewUser(email, "Password1234")
        }
    }

    @Test
    fun 신규_이메일이면_비밀번호_정책을_검증한다() {
        val email = Email("new@example.com")
        whenever(userRepository.existsByEmail(email)).thenReturn(false)

        userDomainService.validateNewUser(email, "Password1234")

        verify(passwordPolicy).validate("Password1234")
    }
}
