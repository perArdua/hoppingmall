package com.hoppingmall.user.auth

import com.hoppingmall.common.BusinessException
import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.support.MySqlContainerSupport
import com.hoppingmall.user.support.RedisContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
@DisplayName("액세스 토큰 없이 리프레시 토큰으로 로그아웃 — 실제 MySQL·Redis")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LogoutWithoutAccessTokenMySqlRedisIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var userCommandService: UserCommandService

    @Test
    fun Authorization_없이_리프레시_토큰으로_로그아웃하면_204이고_그_세션은_재발급되지_않는다() {
        val signIn = authService.login(newAccount(), "10.0.0.1")

        val status = mockMvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refresh_token":"${signIn.refreshToken}"}""")
        ).andReturn().response.status

        val refresh = runCatching { authService.refreshAccessToken(signIn.refreshToken) }
        assertThat(status).isEqualTo(204)
        assertThat((refresh.exceptionOrNull() as? BusinessException)?.errorCode?.status).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun 만료되거나_틀린_액세스_토큰이_와도_리프레시_토큰으로_로그아웃된다() {
        val signIn = authService.login(newAccount(), "10.0.0.1")

        val status = mockMvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer not-a-valid-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refresh_token":"${signIn.refreshToken}"}""")
        ).andReturn().response.status

        assertThat(status).isEqualTo(204)
        assertThat(runCatching { authService.refreshAccessToken(signIn.refreshToken) }.isFailure).isTrue()
    }

    @Test
    fun 토큰_없이_로그아웃하면_401이다() {
        val status = mockMvc.perform(post("/api/v1/auth/logout")).andReturn().response.status

        assertThat(status).isEqualTo(401)
    }

    private fun newAccount(): SignInRequest {
        val email = "logout-${UUID.randomUUID().toString().take(12)}@example.com"
        userCommandService.signUp(SignUpRequest(email = email, password = PASSWORD, name = "로그아웃사용자"))
        return SignInRequest(email = email, password = PASSWORD)
    }

    companion object {
        private const val PASSWORD = "Password1234"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "user_logout_test")
            registry.add("spring.data.redis.host") { RedisContainerSupport.host }
            registry.add("spring.data.redis.port") { RedisContainerSupport.port }
        }
    }
}
