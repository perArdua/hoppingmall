package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.service.PasswordVerifier
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.SignInRequest
import com.hoppingmall.mall.user.dto.response.user.UserProfileResponse
import com.hoppingmall.mall.user.exception.user.UserLoginFailedException
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserQueryServiceImpl(
    private val userRepository: UserRepository,
    private val passwordVerifier: PasswordVerifier,
) : UserQueryService {

    override fun authenticate(request: SignInRequest): User {
        val email = Email(request.email)
        val user = userRepository.findByEmail(email)
            ?: throw UserLoginFailedException()

        passwordVerifier.assertMatches(request.password, user.getPassword())

        return user
    }

    override fun getUserProfile(userId: Long): UserProfileResponse {
        val user = userRepository.findNullableById(userId)
            ?: throw UserNotFoundException()

        return UserProfileResponse(
            id = user.id!!,
            email = user.email.value,
            name = user.getName(),
            role = user.getRole()
        )
    }
}
