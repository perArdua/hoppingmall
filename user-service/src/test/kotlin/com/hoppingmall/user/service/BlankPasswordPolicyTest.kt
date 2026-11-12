package com.hoppingmall.user.service

import com.hoppingmall.user.common.vo.DefaultPasswordPolicy
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.common.vo.WeakPasswordException
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockitoExtension::class)
@DisplayName("공백만으로 된 비밀번호")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BlankPasswordPolicyTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var membershipCommandService: MembershipCommandService

    private val passwordPolicy = DefaultPasswordPolicy()

    private lateinit var userCommandService: UserCommandServiceImpl

    @BeforeEach
    fun setUp() {
        userCommandService = UserCommandServiceImpl(
            userRepository = userRepository,
            passwordCreator = PasswordCreator(BCryptPasswordEncoder(), passwordPolicy),
            userDomainService = UserDomainServiceImpl(userRepository, passwordPolicy),
            membershipCommandService = membershipCommandService
        )
    }

    @Test
    fun 정책은_공백만으로_된_8자_이상_비밀번호를_거절한다() {
        assertThatThrownBy { passwordPolicy.validate("        ") }
            .isInstanceOf(WeakPasswordException::class.java)
        assertThatThrownBy { passwordPolicy.validate("\t\t  \n    ") }
            .isInstanceOf(WeakPasswordException::class.java)
    }

    @Test
    fun 정책은_공백이_섞인_8자_이상_비밀번호는_받는다() {
        assertThatCode { passwordPolicy.validate("pass word 12") }
            .doesNotThrowAnyException()
    }

    @Test
    fun 공백만으로_된_비밀번호로는_가입할_수_없다() {
        val request = SignUpRequest(
            email = "blank-signup@example.com",
            password = "        ",
            name = "공백가입"
        )

        assertThatThrownBy { userCommandService.signUp(request) }
            .isInstanceOf(WeakPasswordException::class.java)
        verify(userRepository, never()).save(any<User>())
    }

    @Test
    fun 공백만으로_된_비밀번호로는_변경할_수_없다() {
        val user = User.fixture(password = Password("encoded-password")).withId(1L)
        whenever(userRepository.findNullableById(1L)).thenReturn(user)

        assertThatThrownBy {
            userCommandService.updateUserProfile(1L, UpdateUserRequest(name = "새이름", password = "        "))
        }.isInstanceOf(WeakPasswordException::class.java)

        assertThat(user.getPassword()).isEqualTo(Password("encoded-password"))
    }
}
