package com.hoppingmall.user.service

import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.PasswordVerifier
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.response.UserProfileResponse
import com.hoppingmall.user.exception.user.UserLoginFailedException
import com.hoppingmall.user.exception.user.UserNotFoundException
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
            role = user.getRole().name
        )
    }
}
