package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.global.vo.password.service.PasswordCreator
import com.hoppingmall.mall.support.withId
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.exception.user.UserAlreadyExistsException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class UserCommandServiceImplTest {

    private val userRepository: UserRepository = mock()
    private val passwordCreator: PasswordCreator = mock()
    private val userDomainService: UserDomainService = mock()
    private val userCommandService = UserCommandServiceImpl(userRepository, passwordCreator, userDomainService)

    @Test
    fun `회원가입 성공 시 사용자 정보가 저장되고 응답이 반환된다`() {
        // given
        val captor = argumentCaptor<User>()
        val request = SignUpRequest(
            email = "test@example.com",
            password = "securePass123",
            name = "홍길동",
            role = Role.SELLER
        )

        whenever(passwordCreator.encode(request.password)).thenReturn(Password("hashed"))
        whenever(userRepository.save(captor.capture())).thenAnswer {
            captor.firstValue.withId(1L)
        }

        // when
        val response = userCommandService.signUp(request)

        // then
        val savedUser = captor.firstValue
        assertEquals(Email("test@example.com"), savedUser.email)
        assertEquals("홍길동", savedUser.getName())
        assertEquals(Role.SELLER, savedUser.getRole())
        assertEquals(1L, response.id)
        verify(userDomainService).validateNewUser(Email(request.email), request.password)
    }

    @Test
    fun `이미 존재하는 이메일이면 예외가 발생한다`() {
        // given
        val request = SignUpRequest(
            email = "dup@example.com",
            password = "securePass123",
            name = "중복유저",
            role = Role.BUYER
        )

        doThrow(UserAlreadyExistsException()).whenever(userDomainService)
            .validateNewUser(Email(request.email), request.password)

        // expect
        assertThrows(UserAlreadyExistsException::class.java) {
            userCommandService.signUp(request)
        }
    }
}
