package com.hoppingmall.mall.user.domain.repository

import com.hoppingmall.mall.global.enums.Role
import com.hoppingmall.mall.global.vo.email.Email
import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.user.domain.User
import com.hoppingmall.mall.support.fixture.fixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

@DataJpaTest
@DisplayName("UserRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserRepositoryTest {

    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
    }

    @Nested
    @DisplayName("findAll")
    inner class FindAll {
        @Test
        fun 모든_사용자_조회_정상_동작() {
            val user1 = User.fixture(
                email = Email("user1@example.com"),
                password = Password("hashedPassword"),
                role = Role.BUYER
            )
            val user2 = User.fixture(
                email = Email("user2@example.com"),
                password = Password("hashedPassword"),
                name = "사용자2",
                role = Role.SELLER
            )

            userRepository.saveAll(listOf(user1, user2))
            testEntityManager.flush()

            val found = userRepository.findAll()

            assertThat(found).hasSize(2)
            assertThat(found).extracting("email").contains(user1.email, user2.email)
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
                password = Password("password123"),
                role = Role.BUYER
            )
            userRepository.save(user)
            testEntityManager.flush()

            assertThat(userRepository.existsByEmail(email)).isTrue()
            assertThat(userRepository.existsByEmail(Email("notfound@example.com"))).isFalse()
        }

    }

    @Nested
    @DisplayName("findByEmail")
    inner class FindByEmail {
        @Test
        fun 이메일로_사용자_조회_성공() {
            val email = Email("test@example.com")
            val user = User.fixture(
                email = email,
                password = Password("password123"),
                name = "테스트 사용자",
                role = Role.BUYER
            )
            userRepository.save(user)
            testEntityManager.flush()

            val found = userRepository.findByEmail(email)

            assertThat(found).isNotNull
            assertThat(found!!.email).isEqualTo(email)
            assertThat(found.getName()).isEqualTo("테스트 사용자")
        }

        @Test
        fun 존재하지_않는_이메일로_조회시_null_반환() {
            val email = Email("nonexistent@example.com")

            val found = userRepository.findByEmail(email)

            assertThat(found).isNull()
        }

    }

    @Nested
    @DisplayName("save")
    inner class Save {
        @Test
        fun 새로운_사용자_저장_성공() {
            val user = User.fixture(
                email = Email("new@example.com"),
                password = Password("newPassword"),
                name = "새 사용자",
                role = Role.BUYER
            )

            val saved = userRepository.save(user)
            testEntityManager.flush()

            assertThat(saved.id).isNotNull
            assertThat(saved.email.value).isEqualTo("new@example.com")
            assertThat(saved.getName()).isEqualTo("새 사용자")
            assertThat(saved.getRole()).isEqualTo(Role.BUYER)
    }

        @Test
        fun 사용자_정보_수정_성공() {
            val user = User.fixture(
                email = Email("update@example.com"),
                password = Password("password"),
                name = "원본 이름",
                role = Role.BUYER
            )
            val saved = userRepository.save(user)
            testEntityManager.flush()

            saved.updateName("수정된 이름")
            val updated = userRepository.save(saved)
            testEntityManager.flush()

            assertThat(updated.getName()).isEqualTo("수정된 이름")
            assertThat(updated.id).isEqualTo(saved.id)
        }
    }

}
