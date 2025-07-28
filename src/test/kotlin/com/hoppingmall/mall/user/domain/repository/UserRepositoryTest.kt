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
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Transactional
@DisplayName("UserRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
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

    @Nested
    @DisplayName("findAll")
    inner class FindAll {
        @Test
        fun 이메일로_사용자_조회_삭제되지_않은_사용자만() {
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

            val found = userRepository.findAll()

            assertThat(found).contains(user)
            assertThat(found).doesNotContain(deletedUser)
        }
    }

    @Nested
    @DisplayName("existsByEmail")
    inner class ExistsByEmail {
        @Test
        fun 이메일_존재_여부_조회() {
            val email = Email("exist@example.com")
            val user = User.fixture(
                email = email,
                password = Password("pw"),
                role = Role.BUYER
            )
            userRepository.save(user)

            assertThat(userRepository.existsByEmail(email)).isTrue()
            assertThat(userRepository.existsByEmail(Email("notfound@example.com"))).isFalse()
        }
    }
}
