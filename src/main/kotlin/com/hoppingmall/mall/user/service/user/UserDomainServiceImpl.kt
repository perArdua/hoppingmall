package com.hoppingmall.mall.user.service.user

import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.policy.PasswordPolicy
import com.hoppingmall.mall.user.domain.repository.UserRepository
import com.hoppingmall.mall.user.exception.user.UserAlreadyExistsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserDomainServiceImpl(
    private val userRepository: UserRepository,
    private val passwordPolicy: PasswordPolicy
) : UserDomainService {

    override fun validateNewUser(email: Email, rawPassword: String) {
        if (userRepository.existsByEmail(email)) {
            throw UserAlreadyExistsException()
        }
        passwordPolicy.validate(rawPassword)
    }
}