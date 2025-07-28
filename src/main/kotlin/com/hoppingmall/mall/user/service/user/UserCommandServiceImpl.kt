package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.global.vo.password.service.PasswordCreator
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.dto.request.user.SignUpRequest
import com.hoppingmall.mall.user.dto.request.user.UpdateUserRequest
import com.hoppingmall.mall.user.dto.response.user.SignUpResponse
import com.hoppingmall.mall.user.exception.user.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserCommandServiceImpl(
    private val userRepository: UserRepository,
    private val passwordCreator: PasswordCreator,
    private val userDomainService: UserDomainService
) : UserCommandService {

    override fun signUp(request: SignUpRequest): SignUpResponse {
        val email = Email(request.email)
        userDomainService.validateNewUser(email, request.password)

        val password = passwordCreator.encode(request.password)

        val user = User.create(
            email = email,
            password = password,
            name = request.name,
            role = request.role
        )

        val saved = userRepository.save(user)

        return SignUpResponse(
            id = saved.id!!,
            email = saved.email.value,
            name = saved.getName(),
            role = saved.getRole()
        )
    }

    override fun updateUserProfile(userId: Long, request: UpdateUserRequest) {
        val user = userRepository.findNullableById(userId)
            ?: throw UserNotFoundException()

        user.updateName(request.name)
        
        request.password?.let { newPassword ->
            val encodedPassword = passwordCreator.encode(newPassword)
            user.updatePassword(encodedPassword)
        }
    }
}
