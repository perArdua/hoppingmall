package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.jwt.TokenProvider
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.PasswordVerifier
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.LoginRequest
import com.hoppingmall.mall.user.dto.response.user.LoginResponse
import com.hoppingmall.mall.user.exception.user.UserLoginFailedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserQueryServiceImpl(
    private val userRepository: UserRepository,
    private val tokenProvider: TokenProvider,
    private val passwordVerifier: PasswordVerifier
) : UserQueryService {

    override fun login(request: LoginRequest): LoginResponse {
        val email = Email(request.email)
        val user = userRepository.findByEmail(email)
            ?: throw UserLoginFailedException()

        if (!passwordVerifier.matches(request.password, user.getPassword())) {
            throw UserLoginFailedException()
        }

        val token = tokenProvider.generateToken(user.id!!, user.getRole())

        return LoginResponse(accessToken = token)
    }
}
