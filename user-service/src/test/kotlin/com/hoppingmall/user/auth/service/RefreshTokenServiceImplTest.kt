package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.SessionProperties
import com.hoppingmall.user.auth.TokenProviderImpl
import com.hoppingmall.user.auth.domain.SessionRevokeReason
import com.hoppingmall.user.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.user.auth.exception.RefreshTokenMismatchException
import com.hoppingmall.user.auth.exception.RefreshTokenNotFoundException
import com.hoppingmall.user.auth.exception.RefreshTokenReusedException
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.exception.user.UserNotFoundException
import com.hoppingmall.user.support.InMemoryUserSessions
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

@ExtendWith(MockitoExtension::class)
@DisplayName("RefreshTokenServiceImpl — 세션별 리프레시 토큰")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenServiceImplTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var legacyRefreshTokenRepository: RefreshTokenRepository

    private val clock = MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusMillis(700))

    private val sessions = InMemoryUserSessions()

    private val jwtProperties = JwtProperties().apply {
        secret = SECRET
        accessExpirationMs = 3600000
        refreshExpirationMs = 86400000
    }

    private val tokenProvider = TokenProviderImpl(jwtProperties)

    private lateinit var service: RefreshTokenServiceImpl

    @BeforeEach
    fun setUp() {
        service = RefreshTokenServiceImpl(
            userSessionRepository = sessions.repository,
            userRepository = userRepository,
            legacyRefreshTokenRepository = legacyRefreshTokenRepository,
            tokenProvider = tokenProvider,
            jwtProperties = jwtProperties,
            sessionProperties = SessionProperties(maxSessionsPerUser = 3, reuseGrace = Duration.ofSeconds(10)),
            clock = clock
        )
        lenient().`when`(userRepository.findForUpdateById(USER_ID)).thenReturn(User.fixture().withId(USER_ID))
    }

    @Test
    fun 로그인하면_새_세션을_만들고_토큰은_해시로만_저장한다() {
        val issued = service.issue(USER_ID)

        val session = sessions.sessions.single()
        assertThat(session.sessionId).isEqualTo(issued.sessionId)
        assertThat(session.tokenHash).hasSize(64).isNotEqualTo(issued.token).doesNotContain(issued.token)
        assertThat(session.generation).isEqualTo(1L)
        assertThat(tokenProvider.parseRefreshClaims(issued.token).sessionId).isEqualTo(issued.sessionId)
    }

    @Test
    fun 사용자가_없으면_세션을_만들지_않는다() {
        whenever(userRepository.findForUpdateById(99L)).thenReturn(null)

        assertThatThrownBy { service.issue(99L) }.isInstanceOf(UserNotFoundException::class.java)
        assertThat(sessions.sessions).isEmpty()
    }

    @Test
    fun 현재_토큰으로_재발급하면_세대를_올리고_새_토큰만_받는다() {
        val issued = service.issue(USER_ID)
        clock.advance(Duration.ofMinutes(30))

        val rotated = service.rotate(issued.token)

        val session = sessions.sessions.single()
        assertThat(rotated.sessionId).isEqualTo(issued.sessionId)
        assertThat(rotated.token).isNotEqualTo(issued.token)
        assertThat(session.generation).isEqualTo(2L)
        assertThat(service.rotate(rotated.token).token).isNotEqualTo(rotated.token)
    }

    @Test
    fun 바뀐_지_유예_시간_안에_직전_토큰이_오면_세션을_그대로_두고_같은_새_토큰을_돌려준다() {
        val issued = service.issue(USER_ID)
        clock.advance(Duration.ofMinutes(30))
        val rotated = service.rotate(issued.token)
        clock.advance(Duration.ofSeconds(10))

        val again = service.rotate(issued.token)

        assertThat(again.token).isEqualTo(rotated.token)
        assertThat(sessions.sessions.single().generation).isEqualTo(2L)
        assertThat(sessions.sessions.single().revokedAt).isNull()
    }

    @Test
    fun 유예_시간이_지난_뒤_직전_토큰이_오면_세션을_끊고_최신_토큰도_거절한다() {
        val issued = service.issue(USER_ID)
        clock.advance(Duration.ofMinutes(30))
        val rotated = service.rotate(issued.token)
        clock.advance(Duration.ofSeconds(12))

        assertThatThrownBy { service.rotate(issued.token) }.isInstanceOf(RefreshTokenReusedException::class.java)
        assertThat(sessions.sessions.single().revokeReason).isEqualTo(SessionRevokeReason.REUSE_DETECTED)
        assertThatThrownBy { service.rotate(rotated.token) }.isInstanceOf(RefreshTokenNotFoundException::class.java)
    }

    @Test
    fun 두_세대_전_토큰은_유예_시간_안이어도_세션을_끊는다() {
        val issued = service.issue(USER_ID)
        clock.advance(Duration.ofSeconds(1))
        val second = service.rotate(issued.token)
        clock.advance(Duration.ofSeconds(1))
        service.rotate(second.token)

        assertThatThrownBy { service.rotate(issued.token) }.isInstanceOf(RefreshTokenReusedException::class.java)
        assertThat(sessions.sessions.single().revokedAt).isNotNull()
    }

    @Test
    fun 상한을_넘으면_가장_오래_쓰지_않은_세션을_끊는다() {
        val first = service.issue(USER_ID)
        clock.advance(Duration.ofMinutes(1))
        val second = service.issue(USER_ID)
        clock.advance(Duration.ofMinutes(1))
        val third = service.issue(USER_ID)
        clock.advance(Duration.ofMinutes(1))
        val firstRotated = service.rotate(first.token)
        clock.advance(Duration.ofMinutes(1))

        service.issue(USER_ID)

        assertThatThrownBy { service.rotate(second.token) }.isInstanceOf(RefreshTokenNotFoundException::class.java)
        assertThat(service.rotate(firstRotated.token).sessionId).isEqualTo(first.sessionId)
        assertThat(service.rotate(third.token).sessionId).isEqualTo(third.sessionId)
        assertThat(sessions.sessions.single { it.sessionId == second.sessionId }.revokeReason).isEqualTo(SessionRevokeReason.EVICTED)
    }

    @Test
    fun 끊기거나_만료된_세션은_다음_로그인_때_지운다() {
        val loggedOut = service.issue(USER_ID)
        service.revokeSession(USER_ID, loggedOut.sessionId)
        val expired = service.issue(USER_ID)
        clock.advance(Duration.ofDays(1).plusSeconds(1))

        val fresh = service.issue(USER_ID)

        assertThat(sessions.sessions.map { it.sessionId }).containsExactly(fresh.sessionId)
        assertThat(expired.sessionId).isNotEqualTo(fresh.sessionId)
    }

    @Test
    fun 다른_사용자의_세션_id를_단_토큰은_거절한다() {
        val issued = service.issue(USER_ID)
        val forged = tokenProvider.generateRefreshToken(
            2L, issued.sessionId, 1L, clock.instant().minusSeconds(1), clock.instant().plus(Duration.ofDays(1))
        )

        assertThatThrownBy { service.rotate(forged) }.isInstanceOf(RefreshTokenNotFoundException::class.java)
        assertThat(sessions.sessions.single().revokedAt).isNull()
    }

    @Test
    fun 로그아웃은_그_세션만_끊는다() {
        val first = service.issue(USER_ID)
        val second = service.issue(USER_ID)

        service.revokeSession(USER_ID, first.sessionId)

        assertThat(sessions.sessions.single { it.sessionId == first.sessionId }.revokeReason).isEqualTo(SessionRevokeReason.LOGOUT)
        assertThat(sessions.sessions.single { it.sessionId == second.sessionId }.revokedAt).isNull()
        verify(legacyRefreshTokenRepository, never()).deleteByUserId(USER_ID)
    }

    @Test
    fun 다른_사용자의_세션은_로그아웃으로_끊지_않는다() {
        val issued = service.issue(USER_ID)

        service.revokeSession(2L, issued.sessionId)

        assertThat(sessions.sessions.single().revokedAt).isNull()
    }

    @Test
    fun 세션_id가_없는_옛_토큰의_로그아웃은_옛_저장값을_지운다() {
        service.revokeSession(USER_ID, null)

        verify(legacyRefreshTokenRepository).deleteByUserId(USER_ID)
    }

    @Test
    fun 모두_끊으면_활성_세션과_옛_저장값이_모두_사라진다() {
        val first = service.issue(USER_ID)
        val second = service.issue(USER_ID)

        service.revokeAll(USER_ID, SessionRevokeReason.PASSWORD_CHANGED)

        assertThat(sessions.sessions.map { it.revokeReason }).containsOnly(SessionRevokeReason.PASSWORD_CHANGED)
        assertThatThrownBy { service.rotate(first.token) }.isInstanceOf(RefreshTokenNotFoundException::class.java)
        assertThatThrownBy { service.rotate(second.token) }.isInstanceOf(RefreshTokenNotFoundException::class.java)
        verify(legacyRefreshTokenRepository).deleteByUserId(USER_ID)
    }

    @Test
    fun 세션_id가_없는_옛_토큰이_저장값과_같으면_새_세션으로_옮긴다() {
        val legacy = legacyToken()
        whenever(legacyRefreshTokenRepository.findByUserId(USER_ID)).thenReturn(legacy)

        val migrated = service.rotate(legacy)

        assertThat(sessions.sessions.single().sessionId).isEqualTo(migrated.sessionId)
        assertThat(service.rotate(migrated.token).sessionId).isEqualTo(migrated.sessionId)
        verify(legacyRefreshTokenRepository).deleteByUserId(USER_ID)
    }

    @Test
    fun 세션_id가_없는_옛_토큰이_저장값과_다르면_거절한다() {
        whenever(legacyRefreshTokenRepository.findByUserId(USER_ID)).thenReturn("other-token")

        assertThatThrownBy { service.rotate(legacyToken()) }.isInstanceOf(RefreshTokenMismatchException::class.java)
        assertThat(sessions.sessions).isEmpty()
    }

    @Test
    fun 세션_id가_없는_옛_토큰의_저장값이_없으면_거절한다() {
        whenever(legacyRefreshTokenRepository.findByUserId(USER_ID)).thenReturn(null)

        assertThatThrownBy { service.rotate(legacyToken()) }.isInstanceOf(RefreshTokenNotFoundException::class.java)
    }

    private fun legacyToken(): String =
        Jwts.builder()
            .setSubject(USER_ID.toString())
            .claim("typ", "refresh")
            .setIssuedAt(Date.from(clock.instant()))
            .setExpiration(Date.from(clock.instant().plus(Duration.ofDays(1))))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private class MutableClock(private var instant: Instant) : Clock() {
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = instant
    }

    companion object {
        private const val USER_ID = 1L
        private const val SECRET = "test-secret-key-that-is-at-least-32-bytes-long-for-hs256"
    }
}
