package com.hoppingmall.user.service

import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.PasswordPolicy
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.exception.user.UserAlreadyExistsException
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
