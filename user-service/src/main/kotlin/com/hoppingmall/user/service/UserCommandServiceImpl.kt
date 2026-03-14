package com.hoppingmall.user.service

import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.dto.response.SignUpResponse
import com.hoppingmall.user.exception.user.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserCommandServiceImpl(
    private val userRepository: UserRepository,
    private val passwordCreator: PasswordCreator,
    private val userDomainService: UserDomainService,
    private val membershipCommandService: MembershipCommandService
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
        membershipCommandService.createMembership(saved.id!!)

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
