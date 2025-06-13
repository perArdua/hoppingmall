package com.hoppingmall.mall.user.domain.repository

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.support.fixture.fixture
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Transactional
class UserRepositoryTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val entityManager: EntityManager
) {

    @BeforeEach
    fun activateFilter() {
        entityManager.unwrap(Session::class.java)
            .enableFilter("softDeleteFilter")
            .setParameter("isDeleted", false)
    }

    @Test
    fun `이메일로 사용자 조회 - 삭제되지 않은 사용자만`() {
        // given
        val user = User.fixture(
            email = Email("test@example.com"),
            password = Password("hashedPassword"),
            role = Role.BUYER
        )
        val deletedUser = User.fixture(
            email = Email("deleted@example.com"),
            password = Password("hashedPassword"),
            name = "삭제된 유저",
            role = Role.SELLER
        ).apply { softDelete() }

        userRepository.saveAll(listOf(user, deletedUser))

        // when
        val found = userRepository.findAll()

        // then
        assertThat(found).contains(user)
        assertThat(found).doesNotContain(deletedUser)
    }

    @Test
    fun `이메일 존재 여부 조회`() {
        // given
        val email = Email("exist@example.com")
        val user = User.fixture(
            email = email,
            password = Password("pw"),
            role = Role.BUYER
        )
        userRepository.save(user)

        // expect
        assertThat(userRepository.existsByEmail(email)).isTrue()
        assertThat(userRepository.existsByEmail(Email("notfound@example.com"))).isFalse()
    }
}
