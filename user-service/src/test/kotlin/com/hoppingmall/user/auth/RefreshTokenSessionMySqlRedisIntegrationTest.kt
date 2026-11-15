package com.hoppingmall.user.auth

import com.hoppingmall.common.BusinessException
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.dto.response.SignInResponse
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.support.MySqlContainerSupport
import com.hoppingmall.user.support.RedisContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@SpringBootTest
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
@DisplayName("세션별 리프레시 토큰 — 실제 MySQL·Redis")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenSessionMySqlRedisIntegrationTest {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var userCommandService: UserCommandService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val executor = Executors.newCachedThreadPool()

    @AfterEach
    fun shutdownExecutor() {
        executor.shutdownNow()
    }

    @Test
    fun 다른_기기에서_로그인해도_먼저_로그인한_기기의_리프레시_토큰으로_재발급된다() {
        val account = newAccount()
        val firstDevice = login(account)
        다음_초까지_기다린다()
        val secondDevice = login(account)

        val firstRefresh = refresh(firstDevice.refreshToken)
        val secondRefresh = refresh(secondDevice.refreshToken)

        assertThat(listOf(firstRefresh.isSuccess, secondRefresh.isSuccess))
            .describedAs("첫 기기=%s, 둘째 기기=%s", firstRefresh, secondRefresh)
            .containsExactly(true, true)
    }

    @Test
    fun 앞_재발급이_끝난_직후_같은_토큰이_다시_와도_둘_다_같은_새_토큰을_받는다() {
        val signIn = login(newAccount())
        다음_초까지_기다린다()

        val firstTab = refresh(signIn.refreshToken)
        val secondTab = refresh(signIn.refreshToken)

        assertThat(listOf(firstTab.isSuccess, secondTab.isSuccess))
            .describedAs("첫 탭=%s, 둘째 탭=%s", firstTab, secondTab)
            .containsExactly(true, true)
        assertThat(secondTab.getOrThrow().refreshToken).isEqualTo(firstTab.getOrThrow().refreshToken)
        assertThat(refresh(secondTab.getOrThrow().refreshToken).isSuccess).isTrue()
    }

    @Test
    fun 같은_토큰의_재발급이_동시에_오면_늦은_요청은_세션_행_잠금을_기다린_뒤_같은_새_토큰을_받는다() {
        val signIn = login(newAccount())
        다음_초까지_기다린다()
        val transactionTemplate = TransactionTemplate(transactionManager)
        val firstRefreshed = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        val first = executor.submit<Result<TokenRefreshResponse>> {
            runCatching {
                transactionTemplate.execute {
                    authService.refreshAccessToken(signIn.refreshToken).also {
                        firstRefreshed.countDown()
                        releaseFirst.await(30, TimeUnit.SECONDS)
                    }
                }!!
            }
        }
        assertThat(firstRefreshed.await(15, TimeUnit.SECONDS)).isTrue()

        val second = executor.submit<Result<TokenRefreshResponse>> {
            runCatching { authService.refreshAccessToken(signIn.refreshToken) }
        }
        val secondWaitedOnLock = awaitLockWaitOrCompletion(second, "같은 토큰의 두 번째 재발급", "user_sessions")
        releaseFirst.countDown()
        val firstResult = first.get(30, TimeUnit.SECONDS)
        val secondResult = second.get(30, TimeUnit.SECONDS)

        assertThat(listOf(secondWaitedOnLock, firstResult.isSuccess, secondResult.isSuccess))
            .describedAs("잠금 대기=%s, 첫째=%s, 둘째=%s", secondWaitedOnLock, firstResult, secondResult)
            .containsExactly(true, true, true)
        assertThat(secondResult.getOrThrow().refreshToken).isEqualTo(firstResult.getOrThrow().refreshToken)
        assertThat(refresh(secondResult.getOrThrow().refreshToken).isSuccess).isTrue()
    }

    @Test
    fun 이미_두_번_바뀐_옛_토큰이_다시_오면_그_세션을_끊어_최신_토큰도_거절된다() {
        val signIn = login(newAccount())
        다음_초까지_기다린다()
        val rotatedOnce = refresh(signIn.refreshToken).getOrThrow()
        val rotatedTwice = refresh(rotatedOnce.refreshToken).getOrThrow()

        val replayed = refresh(signIn.refreshToken)
        val latest = refresh(rotatedTwice.refreshToken)

        assertUnauthorized(replayed)
        assertUnauthorized(latest)
    }

    @Test
    fun 여섯_번째_기기로_로그인하면_가장_오래_쓰지_않은_첫_기기의_세션만_끊긴다() {
        val account = newAccount()
        val devices = (1..6).map { login(account) }

        val results = devices.map { refresh(it.refreshToken) }

        assertUnauthorized(results.first())
        assertThat(results.drop(1).map { it.isSuccess })
            .describedAs("둘째~여섯째 기기=%s", results.drop(1))
            .containsOnly(true)
    }

    @Test
    fun 한_기기에서_로그아웃하면_그_기기의_세션만_끊긴다() {
        val account = newAccount()
        val firstDevice = login(account)
        다음_초까지_기다린다()
        val secondDevice = login(account)

        authService.logout(firstDevice.accessToken)

        assertUnauthorized(refresh(firstDevice.refreshToken))
        assertThat(refresh(secondDevice.refreshToken).isSuccess).isTrue()
    }

    @Test
    fun 비밀번호를_바꾸면_모든_기기의_세션이_끊긴다() {
        val account = newAccount()
        val firstDevice = login(account)
        다음_초까지_기다린다()
        val secondDevice = login(account)

        userCommandService.updateUserProfile(
            account.userId,
            UpdateUserRequest(name = "새이름", password = NEW_PASSWORD, currentPassword = PASSWORD)
        )

        assertUnauthorized(refresh(firstDevice.refreshToken))
        assertUnauthorized(refresh(secondDevice.refreshToken))
    }

    private fun newAccount(): Account {
        val email = "session-${UUID.randomUUID().toString().take(12)}@example.com"
        val signUp = userCommandService.signUp(SignUpRequest(email = email, password = PASSWORD, name = "세션사용자"))
        return Account(signUp.id, SignInRequest(email = email, password = PASSWORD))
    }

    private fun login(account: Account): SignInResponse = authService.login(account.signIn, "10.0.0.1")

    private fun refresh(refreshToken: String): Result<TokenRefreshResponse> =
        runCatching { authService.refreshAccessToken(refreshToken) }

    private fun assertUnauthorized(result: Result<*>) {
        assertThat(result.isFailure).describedAs("결과=%s", result).isTrue()
        assertThat((result.exceptionOrNull() as BusinessException).errorCode.status).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    private fun 다음_초까지_기다린다() {
        val start = Instant.now().epochSecond
        while (Instant.now().epochSecond == start) {
            Thread.onSpinWait()
        }
    }

    private fun awaitLockWaitOrCompletion(pending: Future<*>, actionName: String, waitTable: String): Boolean {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(waitTable) > 0) return true
            if (pending.isDone) return false
            Thread.onSpinWait()
        }
        throw AssertionError("${actionName}가 ${LOCK_WAIT_LIMIT_MS}ms 안에 끝나지도, $waitTable 잠금을 기다리지도 않았다")
    }

    private fun lockWaitCount(waitTable: String): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM performance_schema.data_lock_waits w
            JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
            WHERE l.OBJECT_SCHEMA = DATABASE()
            AND l.OBJECT_NAME = ?
            """.trimIndent(),
            Long::class.java,
            waitTable
        )!!

    private data class Account(val userId: Long, val signIn: SignInRequest)

    companion object {
        private const val PASSWORD = "Password1234"
        private const val NEW_PASSWORD = "NewPassword1234"
        private const val LOCK_WAIT_LIMIT_MS = 15_000L

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "user_session_test")
            registry.add("spring.data.redis.host") { RedisContainerSupport.host }
            registry.add("spring.data.redis.port") { RedisContainerSupport.port }
        }
    }
}
